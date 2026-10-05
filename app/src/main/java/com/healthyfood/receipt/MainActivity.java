package com.healthyfood.receipt;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class MainActivity extends Activity {
    EditText amount, product;
    TextView baseText, gstText, totalText, printerText;
    BluetoothAdapter bt;
    BluetoothDevice selectedPrinter;
    static final int REQ_BT = 100;
    static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    android.content.SharedPreferences prefs;

    int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    TextView tv(String text, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    GradientDrawable bg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildUi();
        bt = BluetoothAdapter.getDefaultAdapter();
        requestBtPermission();
        autoLoadPrinter();
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(16));

        // Protect the UI from the Android status/navigation bars (especially Android 15).
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
            int bottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            root.setPadding(dp(20), top + dp(8), dp(20), bottom + dp(12));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.healthy_food_logo);
        logo.setColorFilter(new ColorMatrixColorFilter(grayMatrix()));
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView title = tv("HEALTHY FOOD\nCash Receipt", 19, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(60), 1);
        titleLp.setMargins(dp(10), 0, 0, 0);
        header.addView(title, titleLp);
        root.addView(header);

        TextView sub = tv("58 mm Bluetooth thermal printer", 12, false);
        sub.setTextColor(Color.DKGRAY); sub.setGravity(Gravity.CENTER);
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(24)));

        root.addView(label("Product / service (optional)"));
        product = new EditText(this);
        product.setHint("e.g. Veg Thali");
        product.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        product.setSingleLine(true);
        product.setPadding(dp(12), 0, dp(12), 0);
        root.addView(product, fieldParams(48));

        root.addView(label("TOTAL amount paid (5% IGST included)"));
        amount = new EditText(this);
        amount.setHint("Enter amount");
        amount.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        amount.setGravity(Gravity.CENTER);
        amount.setInputType(2 | 8192);
        amount.setSingleLine(true);
        amount.setPadding(dp(12), 0, dp(12), 0);
        root.addView(amount, fieldParams(56));
        amount.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ updateCalc(); }
            public void afterTextChanged(Editable e){}
        });

        LinearLayout calc = new LinearLayout(this);
        calc.setOrientation(LinearLayout.VERTICAL);
        calc.setPadding(dp(12), dp(8), dp(12), dp(5));
        calc.setBackground(bg(Color.rgb(247,247,247), 8));
        baseText = addRow(calc, "Amount before IGST", "₹0.00", false);
        gstText = addRow(calc, "IGST @ 5%", "₹0.00", false);
        totalText = addRow(calc, "TOTAL PAID", "₹0.00", true);
        LinearLayout.LayoutParams calcLp = new LinearLayout.LayoutParams(-1, -2);
        calcLp.setMargins(0, dp(8), 0, dp(5));
        root.addView(calc, calcLp);

        printerText = tv("Printer: not selected", 12, false);
        printerText.setTextColor(Color.DKGRAY);
        printerText.setGravity(Gravity.CENTER);
        root.addView(printerText, new LinearLayout.LayoutParams(-1, dp(28)));

        Button select = button("SELECT PRINTER", Color.rgb(235,235,235), Color.BLACK);
        select.setOnClickListener(v -> choosePrinter());
        root.addView(select, buttonParams(48));

        Button print = button("PRINT CASH RECEIPT", Color.BLACK, Color.WHITE);
        print.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        LinearLayout.LayoutParams printLp = buttonParams(54);
        printLp.setMargins(0, dp(7), 0, 0);
        root.addView(print, printLp);
        print.setOnClickListener(v -> printReceipt());

        Button clear = button("CLEAR", Color.rgb(235,235,235), Color.BLACK);
        LinearLayout.LayoutParams clearLp = buttonParams(44);
        clearLp.setMargins(0, dp(6), 0, 0);
        root.addView(clear, clearLp);
        clear.setOnClickListener(v -> {
            product.setText(""); amount.setText(""); amount.requestFocus();
        });

        TextView footer = tv("First time: select printer. After that: amount → PRINT", 11, false);
        footer.setTextColor(Color.DKGRAY); footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams f = new LinearLayout.LayoutParams(-1, dp(30));
        f.setMargins(0, dp(3), 0, 0); root.addView(footer, f);

        scroll.addView(root);
        setContentView(scroll);
    }

    TextView label(String s) {
        TextView t = tv(s, 12, true);
        t.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(28));
        p.setMargins(dp(2), dp(6), dp(2), 0);
        t.setGravity(Gravity.BOTTOM);
        t.setLayoutParams(p);
        return t;
    }

    LinearLayout.LayoutParams fieldParams(int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(h));
        return p;
    }

    Button button(String text, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(textColor);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(bg(bgColor, 8));
        return b;
    }

    LinearLayout.LayoutParams buttonParams(int h) {
        return new LinearLayout.LayoutParams(-1, dp(h));
    }

    TextView addRow(LinearLayout parent, String left, String right, boolean bold) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = tv(left, bold ? 15 : 13, bold);
        TextView c = tv(right, bold ? 15 : 13, bold);
        c.setGravity(Gravity.RIGHT);
        row.addView(a, new LinearLayout.LayoutParams(0, dp(34), 1));
        row.addView(c, new LinearLayout.LayoutParams(0, dp(34), 1));
        parent.addView(row);
        return c;
    }

    void updateCalc() {
        double total = round2(parse());
        double base = round2(total / 1.05);
        double gst = round2(total - base);
        baseText.setText(money(base));
        gstText.setText(money(gst));
        totalText.setText(money(total));
    }

    double parse() {
        try { return Double.parseDouble(amount.getText().toString()); }
        catch(Exception e){ return 0; }
    }

    double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    String money(double x) { return String.format(Locale.US, "Rs. %.2f", x); }

    void requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQ_BT);
        }
    }

    void autoLoadPrinter() {
        String mac = prefs.getString("printer_mac", "");
        if (mac.isEmpty() || bt == null) return;
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        try {
            selectedPrinter = bt.getRemoteDevice(mac);
            String n = selectedPrinter.getName();
            printerText.setText("Printer: " + (n == null ? "saved printer" : n));
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
        new AlertDialog.Builder(this).setTitle("Select 58mm printer")
            .setItems(names, (d, which) -> {
                selectedPrinter = list.get(which);
                prefs.edit().putString("printer_mac", selectedPrinter.getAddress()).apply();
                printerText.setText("Printer: " + selectedPrinter.getName());
                toast("Printer saved.");
            }).show();
    }

    void printReceipt() {
        double enteredTotal = parse();
        if (enteredTotal <= 0) { toast("Please enter the total amount."); amount.requestFocus(); return; }
        if (selectedPrinter == null) { choosePrinter(); return; }

        final String productName = product.getText().toString().trim();
        final double total = round2(enteredTotal);
        final double base = round2(total / 1.05);
        final double gst = round2(total - base);

        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                if (Build.VERSION.SDK_INT >= 31 &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> toast("Bluetooth permission is required."));
                    return;
                }
                socket = selectedPrinter.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                OutputStream out = socket.getOutputStream();
                out.write(new byte[]{0x1B,0x40});
                out.write(center());
                out.write(text("HEALTHY FOOD GETS BETTER\n"));
                out.write(text("GST: 06KOWPS3125E2ZF\n"));
                out.write(text("FSSAI: 22724926000547\n"));
                out.write(text(dateTimeLine() + "\n"));
                out.write(text("------------------------------\n"));
                if (!productName.isEmpty()) {
                    out.write(text("Product: " + fit(productName, 24) + "\n"));
                    out.write(text("------------------------------\n"));
                }
                out.write(leftRight("Amount:", money(base)));
                out.write(leftRight("IGST @ 5%:", money(gst)));
                out.write(text("------------------------------\n"));
                out.write(bold(true));
                out.write(leftRight("TOTAL PAID:", money(total)));
                out.write(bold(false));
                out.write(text("PAYMENT: CASH\n"));
                out.write(text("------------------------------\n"));
                out.write(center());
                
                out.write(text("Please visit again..!!\n\n\n"));
                out.write(cut());
                out.flush(); out.close(); socket.close();
                runOnUiThread(() -> { toast("Receipt printed."); amount.setText(""); product.setText(""); amount.requestFocus(); });
            } catch(Exception e) {
                try { if(socket != null) socket.close(); } catch(Exception ignored) {}
                runOnUiThread(() -> toast("Printing failed. Check printer is ON and paired."));
            }
        }).start();
    }

    String dateTimeLine() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yy   'Time:' hh:mm a", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return "Date: " + sdf.format(new Date());
    }

    String fit(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }
    byte[] center(){ return new byte[]{0x1B,0x61,0x01}; }
    byte[] bold(boolean on){ return new byte[]{0x1B,0x45,(byte)(on?1:0)}; }
    byte[] text(String s){ return s.getBytes(StandardCharsets.UTF_8); }
    byte[] leftRight(String a,String b){
        int width=32, spaces=Math.max(1,width-a.length()-b.length());
        return text(a + " ".repeat(spaces) + b + "\n");
    }
    byte[] cut(){ return new byte[]{0x1D,0x56,0x00}; }
    ColorMatrix grayMatrix(){ ColorMatrix m=new ColorMatrix(); m.setSaturation(0); return m; }
    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
}
