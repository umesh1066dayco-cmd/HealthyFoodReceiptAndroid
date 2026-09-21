package com.healthyfood.receipt;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    EditText amount, product;
    TextView baseText, gstText, totalText, printerText;
    BluetoothAdapter bt;
    BluetoothDevice selectedPrinter;
    static final int REQ_BT = 100;
    static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        buildUi();
        bt = BluetoothAdapter.getDefaultAdapter();
        requestBtPermission();
        // Printer loading is also retried from onRequestPermissionsResult() because
        // Android 12+ permission dialogs are asynchronous.
        autoLoadPrinter();
    }

    void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 16);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("HEALTHY FOOD\nCash Receipt");
        title.setTextSize(23); title.setTextColor(Color.BLACK); title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.healthyfood.receipt.R.drawable.healthy_food_logo);
        logo.setColorFilter(new ColorMatrixColorFilter(grayMatrix()));
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams lpLogo = new LinearLayout.LayoutParams(-1, 125);
        lpLogo.setMargins(0, 5, 0, 2);
        root.addView(logo, lpLogo);

        TextView hint = new TextView(this);
        hint.setText("Optional product / service name");
        hint.setTextSize(13); hint.setGravity(Gravity.CENTER);
        root.addView(hint);

        product = new EditText(this);
        product.setHint("e.g. Veg Thali");
        product.setSingleLine(true);
        product.setTextSize(17);
        root.addView(product, new LinearLayout.LayoutParams(-1, 58));

        TextView amountHint = new TextView(this);
        amountHint.setText("Enter TOTAL amount paid (5% IGST included)");
        amountHint.setTextSize(13); amountHint.setGravity(Gravity.CENTER);
        root.addView(amountHint);

        amount = new EditText(this);
        amount.setHint("₹ 0.00");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(28); amount.setGravity(Gravity.CENTER);
        amount.setSingleLine(true);
        root.addView(amount, new LinearLayout.LayoutParams(-1, 68));
        amount.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ updateCalc(); }
            public void afterTextChanged(android.text.Editable e){}
        });

        LinearLayout calc = new LinearLayout(this);
        calc.setOrientation(LinearLayout.VERTICAL);
        calc.setPadding(10, 5, 10, 2);
        baseText = addRow(calc, "Amount before IGST:", "₹0.00");
        gstText = addRow(calc, "IGST @ 5%:", "₹0.00");
        totalText = addRow(calc, "TOTAL PAID:", "₹0.00");
        root.addView(calc);

        printerText = new TextView(this);
        printerText.setText("Printer: not selected");
        printerText.setTextSize(12);
        printerText.setGravity(Gravity.CENTER);
        printerText.setPadding(0, 4, 0, 4);
        root.addView(printerText);

        Button select = new Button(this);
        select.setText("SELECT PRINTER (FIRST TIME)");
        select.setOnClickListener(v -> choosePrinter());
        root.addView(select, new LinearLayout.LayoutParams(-1, 52));

        Button print = new Button(this);
        print.setText("PRINT CASH RECEIPT");
        print.setTextSize(18); print.setTextColor(Color.WHITE);
        print.setBackgroundColor(Color.BLACK);
        print.setOnClickListener(v -> printReceipt());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 62);
        p.setMargins(0, 8, 0, 0); root.addView(print, p);

        Button clear = new Button(this);
        clear.setText("CLEAR");
        clear.setOnClickListener(v -> {
            product.setText("");
            amount.setText("");
            amount.requestFocus();
        });
        root.addView(clear, new LinearLayout.LayoutParams(-1, 50));

        TextView footer = new TextView(this);
        footer.setText("After first printer selection, just enter amount → PRINT");
        footer.setTextSize(11); footer.setGravity(Gravity.CENTER);
        footer.setTextColor(Color.DKGRAY);
        root.addView(footer);

        setContentView(root);
    }

    TextView addRow(LinearLayout parent, String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView a = new TextView(this); a.setText(left); a.setTextSize(14);
        TextView c = new TextView(this); c.setText(right); c.setTextSize(14); c.setGravity(Gravity.RIGHT);
        row.addView(a, new LinearLayout.LayoutParams(0, 40, 1));
        row.addView(c, new LinearLayout.LayoutParams(0, 40, 1));
        parent.addView(row);
        return c;
    }

    void updateCalc() {
        double total = parse();
        double base = total / 1.05;
        double gst = total - base;
        baseText.setText(money(base));
        gstText.setText(money(gst));
        totalText.setText(money(total));
    }

    double parse() {
        try { return Double.parseDouble(amount.getText().toString()); }
        catch(Exception e){ return 0; }
    }

    String money(double x) { return String.format(Locale.US, "Rs.%.2f", x); }

    void requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQ_BT);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_BT) {
            boolean granted = true;
            for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) granted = false;
            if (granted) autoLoadPrinter();
            else toast("Bluetooth permission is required to print.");
        }
    }

    void autoLoadPrinter() {
        String mac = prefs.getString("printer_mac", "");
        if (mac.isEmpty() || bt == null) return;
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        try {
            selectedPrinter = bt.getRemoteDevice(mac);
            printerText.setText("Printer: " + selectedPrinter.getName() + " (saved)");
        } catch(Exception ignored) {}
    }

    void choosePrinter() {
        if (bt == null) { toast("This phone does not support Bluetooth."); return; }
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBtPermission(); return;
        }
        Set<BluetoothDevice> devices = bt.getBondedDevices();
        if (devices == null || devices.isEmpty()) {
            toast("Pair your 58mm printer in Android Bluetooth settings first.");
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            return;
        }
        ArrayList<BluetoothDevice> list = new ArrayList<>(devices);
        String[] names = new String[list.size()];
        for(int i=0;i<list.size();i++) {
            String n = list.get(i).getName();
            names[i] = (n == null ? "Bluetooth device" : n) + "\n" + list.get(i).getAddress();
        }

        new AlertDialog.Builder(this).setTitle("Select 58mm thermal printer")
            .setItems(names, (d, which) -> {
                selectedPrinter = list.get(which);
                prefs.edit().putString("printer_mac", selectedPrinter.getAddress()).apply();
                printerText.setText("Printer: " + selectedPrinter.getName() + " (saved)");
                toast("Printer saved. Next time you can just press PRINT.");
            }).show();
    }

    void printReceipt() {
        double total = parse();
        if (total <= 0) {
            toast("Please enter the total amount.");
            amount.requestFocus();
            return;
        }
        if (selectedPrinter == null) {
            choosePrinter();
            return;
        }

        String productName = product.getText().toString().trim();
        double base = total / 1.05, gst = total - base;
        final BluetoothDevice printer = selectedPrinter;

        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                if (Build.VERSION.SDK_INT >= 31 &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> toast("Bluetooth permission is required."));
                    return;
                }

                // Discovery can interfere with RFCOMM connection. Stop it first.
                try { if (bt != null && bt.isDiscovering()) bt.cancelDiscovery(); } catch(Exception ignored) {}

                // Different 58mm printers expose slightly different RFCOMM services.
                // Try the standard SPP service first, then insecure SPP, then channel 1.
                Exception last = null;
                BluetoothSocket[] candidates = new BluetoothSocket[3];
                try { candidates[0] = printer.createInsecureRfcommSocketToServiceRecord(SPP_UUID); } catch(Exception e) { last = e; }
                try { candidates[1] = printer.createRfcommSocketToServiceRecord(SPP_UUID); } catch(Exception e) { last = e; }
                try {
                    java.lang.reflect.Method m = printer.getClass().getMethod("createRfcommSocket", int.class);
                    candidates[2] = (BluetoothSocket)m.invoke(printer, 1);
                } catch(Exception e) { last = e; }

                for (BluetoothSocket candidate : candidates) {
                    if (candidate == null) continue;
                    try {
                        candidate.connect();
                        socket = candidate;
                        break;
                    } catch(Exception e) {
                        last = e;
                        try { candidate.close(); } catch(Exception ignored) {}
                    }
                }

                if (socket == null || !socket.isConnected()) {
                    throw new IOException("Unable to connect to printer", last);
                }

                OutputStream out = socket.getOutputStream();
                // Give some low-cost thermal printers time to finish Bluetooth setup.
                try { Thread.sleep(800); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }

                out.write(new byte[]{0x1B,0x40}); // initialize
                out.write(center());
                out.write(text("HEALTHY FOOD GETS BETTER\n"));
                out.write(text("GST: 06KOWPS3125E2ZF\n"));
                out.write(text("FSSAI: 22724926000547\n"));
                out.write(text("--------------------------------\n"));

                if (!productName.isEmpty()) {
                    out.write(text("Product: " + fit(safeText(productName), 24) + "\n"));
                    out.write(text("--------------------------------\n"));
                }

                out.write(leftRight("Amount:", money(base)));
                out.write(leftRight("IGST @ 5%:", money(gst)));
                out.write(text("--------------------------------\n"));
                out.write(bold(true));
                out.write(leftRight("TOTAL PAID:", money(total)));
                out.write(bold(false));
                out.write(text("PAYMENT: CASH\n"));
                out.write(text("--------------------------------\n"));
                out.write(center());
                out.write(text("Thank you for the service..!!\n"));
                out.write(text("Please visit again..!!\n\n\n"));
                // Do not send a cutter command: many low-cost 58mm printers do not
                // have a cutter and can stop/ignore the job when ESC/POS cut is sent.
                out.flush();
                try { Thread.sleep(700); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
                out.close();
                socket.close();

                runOnUiThread(() -> {
                    toast("Receipt sent to printer.");
                    amount.setText("");
                    product.setText("");
                    amount.requestFocus();
                });
            } catch(Exception e) {
                try { if(socket != null) socket.close(); } catch(Exception ignored) {}
                String detail = e.getMessage();
                if (detail == null || detail.trim().isEmpty()) detail = e.getClass().getSimpleName();
                final String msg = detail;
                runOnUiThread(() -> toast("Printing failed: " + msg + "\nCheck printer is ON, paired and Bluetooth Classic/ESC-POS is supported."));
            }
        }).start();
    }

    String fit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // Most small ESC/POS printers do not support the Unicode ₹ glyph reliably.
    // Use printer-safe ASCII for the receipt instead of risking an invalid byte sequence.
    String safeText(String s) {
        return s.replace("₹", "Rs.").replaceAll("[^\\x20-\\x7E]", "?");
    }

    byte[] center(){ return new byte[]{0x1B,0x61,0x01}; }
    byte[] bold(boolean on){ return new byte[]{0x1B,0x45,(byte)(on?1:0)}; }
    byte[] text(String s){ return s.getBytes(StandardCharsets.UTF_8); }

    byte[] leftRight(String a,String b){
        int width=32, spaces=Math.max(1,width-a.length()-b.length());
        return text(a + " ".repeat(spaces) + b + "\n");
    }

    byte[] cut(){ return new byte[]{0x1D,0x56,0x00}; }

    ColorMatrix grayMatrix(){
        ColorMatrix m=new ColorMatrix(); m.setSaturation(0); return m;
    }

    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
}
