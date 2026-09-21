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

public class MainActivity extends Activity {

    EditText amount, product;

    TextView baseText, gstText, totalText, printerText;

    BluetoothAdapter bt;
    BluetoothDevice selectedPrinter;

    static final int REQ_BT = 100;

    static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    android.content.SharedPreferences prefs;


    // =========================================================
    // ACTIVITY START
    // =========================================================

    @Override
    public void onCreate(Bundle b) {

        super.onCreate(b);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        buildUi();

        bt = BluetoothAdapter.getDefaultAdapter();

        requestBtPermission();

        autoLoadPrinter();
    }


    // =========================================================
    // DP HELPER
    // =========================================================

    int dp(int value) {

        return (int) (
                value *
                getResources().getDisplayMetrics().density
                + 0.5f
        );
    }


    // =========================================================
    // MAIN USER INTERFACE
    // =========================================================

    void buildUi() {

        ScrollView scroll = new ScrollView(this);

        scroll.setFillViewport(true);

        scroll.setBackgroundColor(Color.WHITE);


        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(
                dp(20),
                dp(16),
                dp(20),
                dp(16)
        );

        root.setBackgroundColor(Color.WHITE);


        // -----------------------------------------------------
        // HEADER
        // -----------------------------------------------------

        TextView title = new TextView(this);

        title.setText(
                "HEALTHY FOOD\nCash Receipt"
        );

        title.setTextSize(23);

        title.setTextColor(Color.BLACK);

        title.setGravity(Gravity.CENTER);

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );


        // -----------------------------------------------------
        // LOGO
        // -----------------------------------------------------

        ImageView logo = new ImageView(this);

        logo.setImageResource(
                com.healthyfood.receipt.R.drawable.healthy_food_logo
        );

        logo.setColorFilter(
                new ColorMatrixColorFilter(grayMatrix())
        );

        logo.setAdjustViewBounds(true);

        logo.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );


        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(100)
                );

        logoParams.setMargins(
                0,
                dp(6),
                0,
                dp(4)
        );

        root.addView(
                logo,
                logoParams
        );


        // -----------------------------------------------------
        // GST + FSSAI
        // -----------------------------------------------------

        TextView registration = new TextView(this);

        registration.setText(
                "GST: 06KOWPS3125E2ZF\n" +
                "FSSAI: 22724926000547"
        );

        registration.setTextSize(12);

        registration.setTextColor(Color.DKGRAY);

        registration.setGravity(Gravity.CENTER);

        registration.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams registrationParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                );

        registrationParams.setMargins(
                0,
                dp(2),
                0,
                dp(6)
        );

        root.addView(
                registration,
                registrationParams
        );


        // -----------------------------------------------------
        // PRODUCT LABEL
        // -----------------------------------------------------

        TextView productLabel = new TextView(this);

        productLabel.setText(
                "Optional product / service name"
        );

        productLabel.setTextSize(13);

        productLabel.setTextColor(Color.DKGRAY);

        productLabel.setGravity(Gravity.CENTER);

        root.addView(
                productLabel,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(30)
                )
        );


        // -----------------------------------------------------
        // PRODUCT INPUT
        // -----------------------------------------------------

        product = new EditText(this);

        product.setHint(
                "e.g. Veg Thali"
        );

        product.setSingleLine(true);

        product.setTextSize(16);

        product.setPadding(
                dp(12),
                0,
                dp(12),
                0
        );

        root.addView(
                product,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(52)
                )
        );


        // -----------------------------------------------------
        // AMOUNT LABEL
        // -----------------------------------------------------

        TextView amountLabel = new TextView(this);

        amountLabel.setText(
                "Enter TOTAL amount paid (5% IGST included)"
        );

        amountLabel.setTextSize(13);

        amountLabel.setTextColor(Color.DKGRAY);

        amountLabel.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams amountLabelParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(32)
                );

        amountLabelParams.setMargins(
                0,
                dp(8),
                0,
                0
        );

        root.addView(
                amountLabel,
                amountLabelParams
        );


        // -----------------------------------------------------
        // AMOUNT INPUT
        // -----------------------------------------------------

        amount = new EditText(this);

        amount.setHint(
                "₹ 0.00"
        );

        amount.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        amount.setTextSize(26);

        amount.setGravity(
                Gravity.CENTER
        );

        amount.setSingleLine(true);

        amount.setPadding(
                dp(8),
                0,
                dp(8),
                0
        );

        root.addView(
                amount,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(60)
                )
        );


        amount.addTextChangedListener(
                new android.text.TextWatcher() {

                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after) {
                    }

                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count) {

                        updateCalc();
                    }

                    public void afterTextChanged(
                            android.text.Editable e) {
                    }
                }
        );


        // -----------------------------------------------------
        // CALCULATION BOX
        // -----------------------------------------------------

        LinearLayout calc =
                new LinearLayout(this);

        calc.setOrientation(
                LinearLayout.VERTICAL
        );

        calc.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
        );

        calc.setBackgroundColor(
                Color.rgb(245, 245, 245)
        );


        baseText = addRow(
                calc,
                "Amount before IGST:",
                "Rs.0.00"
        );


        gstText = addRow(
                calc,
                "IGST @ 5%:",
                "Rs.0.00"
        );


        totalText = addRow(
                calc,
                "TOTAL PAID:",
                "Rs.0.00"
        );


        LinearLayout.LayoutParams calcParams =
                new LinearLayout.LayoutParams(
                        -1,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        calcParams.setMargins(
                0,
                dp(8),
                0,
                dp(8)
        );


        root.addView(
                calc,
                calcParams
        );


        // -----------------------------------------------------
        // PRINTER STATUS
        // -----------------------------------------------------

        printerText = new TextView(this);

        printerText.setText(
                "Printer: not selected"
        );

        printerText.setTextSize(13);

        printerText.setTextColor(
                Color.DKGRAY
        );

        printerText.setGravity(
                Gravity.CENTER
        );

        printerText.setPadding(
                0,
                dp(6),
                0,
                dp(6)
        );


        root.addView(
                printerText,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(36)
                )
        );


        // -----------------------------------------------------
        // SELECT PRINTER BUTTON
        // -----------------------------------------------------

        Button select = new Button(this);

        select.setText(
                "SELECT PRINTER"
        );

        select.setTextSize(14);

        select.setOnClickListener(
                v -> choosePrinter()
        );


        root.addView(
                select,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(52)
                )
        );


        // -----------------------------------------------------
        // PRINT BUTTON
        // -----------------------------------------------------

        Button print = new Button(this);

        print.setText(
                "PRINT CASH RECEIPT"
        );

        print.setTextSize(18);

        print.setTextColor(
                Color.WHITE
        );

        print.setBackgroundColor(
                Color.BLACK
        );


        print.setOnClickListener(
                v -> printReceipt()
        );


        LinearLayout.LayoutParams printParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(58)
                );

        printParams.setMargins(
                0,
                dp(10),
                0,
                0
        );


        root.addView(
                print,
                printParams
        );


        // -----------------------------------------------------
        // CLEAR BUTTON
        // -----------------------------------------------------

        Button clear = new Button(this);

        clear.setText(
                "CLEAR"
        );

        clear.setTextSize(14);


        clear.setOnClickListener(v -> {

            product.setText("");

            amount.setText("");

            amount.requestFocus();

        });


        LinearLayout.LayoutParams clearParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                );

        clearParams.setMargins(
                0,
                dp(4),
                0,
                0
        );


        root.addView(
                clear,
                clearParams
        );


        // -----------------------------------------------------
        // FOOTER
        // -----------------------------------------------------

        TextView footer = new TextView(this);

        footer.setText(
                "After first printer selection,\n" +
                "just enter amount → PRINT"
        );

        footer.setTextSize(11);

        footer.setGravity(
                Gravity.CENTER
        );

        footer.setTextColor(
                Color.DKGRAY
        );

        footer.setPadding(
                0,
                dp(8),
                0,
                dp(4)
        );


        root.addView(
                footer,
                new LinearLayout.LayoutParams(
                        -1,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );


        // -----------------------------------------------------
        // SET SCREEN
        // -----------------------------------------------------

        scroll.addView(root);

        setContentView(scroll);
    }


    // =========================================================
    // CALCULATION ROW
    // =========================================================

    TextView addRow(
            LinearLayout parent,
            String left,
            String right) {


        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView leftText =
                new TextView(this);

        leftText.setText(left);

        leftText.setTextSize(14);

        leftText.setTextColor(
                Color.BLACK
        );

        leftText.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView rightText =
                new TextView(this);

        rightText.setText(right);

        rightText.setTextSize(14);

        rightText.setTextColor(
                Color.BLACK
        );

        rightText.setGravity(
                Gravity.RIGHT |
                Gravity.CENTER_VERTICAL
        );


        row.addView(
                leftText,
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1
                )
        );


        row.addView(
                rightText,
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1
                )
        );


        parent.addView(row);


        return rightText;
    }


    // =========================================================
    // TAX CALCULATION
    // =========================================================

    void updateCalc() {

        double total = parse();

        double base =
                total / 1.05;

        double gst =
                total - base;


        baseText.setText(
                money(base)
        );

        gstText.setText(
                money(gst)
        );

        totalText.setText(
                money(total)
        );
    }


    double parse() {

        try {

            return Double.parseDouble(
                    amount.getText().toString()
            );

        } catch (Exception e) {

            return 0;
        }
    }


    String money(double x) {

        return String.format(
                Locale.US,
                "Rs.%.2f",
                x
        );
    }


    // =========================================================
    // BLUETOOTH PERMISSION
    // =========================================================

    void requestBtPermission() {

        if (
                Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    },
                    REQ_BT
            );
        }
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] results) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                results
        );


        if (requestCode == REQ_BT) {

            boolean granted = true;


            for (int r : results) {

                if (
                        r != PackageManager.PERMISSION_GRANTED
                ) {

                    granted = false;
                }
            }


            if (granted) {

                autoLoadPrinter();

            } else {

                toast(
                        "Bluetooth permission is required to print."
                );
            }
        }
    }


    // =========================================================
    // LOAD SAVED PRINTER
    // =========================================================

    void autoLoadPrinter() {

        String mac =
                prefs.getString(
                        "printer_mac",
                        ""
                );


        if (
                mac.isEmpty() ||
                bt == null
        ) {

            return;
        }


        if (
                Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
        ) {

            return;
        }


        try {

            selectedPrinter =
                    bt.getRemoteDevice(mac);


            String name =
                    selectedPrinter.getName();


            if (name == null ||
                    name.trim().isEmpty()) {

                name = "MPT-II";
            }


            printerText.setText(
                    "Printer: " +
                    name +
                    " (saved)"
            );


        } catch (Exception ignored) {
        }
    }


    // =========================================================
    // SELECT PRINTER
    // =========================================================

    void choosePrinter() {

        if (bt == null) {

            toast(
                    "This phone does not support Bluetooth."
            );

            return;
        }


        if (
                Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestBtPermission();

            return;
        }


        Set<BluetoothDevice> devices =
                bt.getBondedDevices();


        if (
                devices == null ||
                devices.isEmpty()
        ) {

            toast(
                    "Pair your 58mm printer in Android Bluetooth settings first."
            );


            startActivity(
                    new Intent(
                            Settings.ACTION_BLUETOOTH_SETTINGS
                    )
            );


            return;
        }


        ArrayList<BluetoothDevice> list =
                new ArrayList<>(devices);


        String[] names =
                new String[list.size()];


        for (
                int i = 0;
                i < list.size();
                i++
        ) {

            String n =
                    list.get(i).getName();


            names[i] =
                    (
                            n == null
                                    ? "Bluetooth device"
                                    : n
                    )
                    +
                    "\n"
                    +
                    list.get(i).getAddress();
        }


        new AlertDialog.Builder(this)

                .setTitle(
                        "Select 58mm thermal printer"
                )

                .setItems(
                        names,
                        (d, which) -> {

                            selectedPrinter =
                                    list.get(which);


                            prefs.edit()
                                    .putString(
                                            "printer_mac",
                                            selectedPrinter.getAddress()
                                    )
                                    .apply();


                            String name =
                                    selectedPrinter.getName();


                            if (
                                    name == null ||
                                    name.trim().isEmpty()
                            ) {

                                name = "MPT-II";
                            }


                            printerText.setText(
                                    "Printer: " +
                                    name +
                                    " (saved)"
                            );


                            toast(
                                    "Printer saved. Next time you can just press PRINT."
                            );
                        }
                )

                .show();
    }


    // =========================================================
    // PRINT RECEIPT
    // =========================================================

    void printReceipt() {

        double total = parse();


        if (total <= 0) {

            toast(
                    "Please enter the total amount."
            );

            amount.requestFocus();

            return;
        }


        if (selectedPrinter == null) {

            choosePrinter();

            return;
        }


        String productName =
                product.getText()
                        .toString()
                        .trim();


        double base =
                total / 1.05;


        double gst =
                total - base;


        final BluetoothDevice printer =
                selectedPrinter;


        new Thread(() -> {

            BluetoothSocket socket = null;


            try {

                // -------------------------------------------------
                // BLUETOOTH PERMISSION
                // -------------------------------------------------

                if (
                        Build.VERSION.SDK_INT >= 31 &&
                        checkSelfPermission(
                                Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                ) {

                    runOnUiThread(
                            () -> toast(
                                    "Bluetooth permission is required."
                            )
                    );

                    return;
                }


                // -------------------------------------------------
                // STOP BLUETOOTH DISCOVERY
                // -------------------------------------------------

                try {

                    if (
                            bt != null &&
                            bt.isDiscovering()
                    ) {

                        bt.cancelDiscovery();
                    }

                } catch (Exception ignored) {
                }


                // -------------------------------------------------
                // TRY DIFFERENT SPP CONNECTION METHODS
                // -------------------------------------------------

                Exception last = null;


                BluetoothSocket[] candidates =
                        new BluetoothSocket[3];


                try {

                    candidates[0] =
                            printer
                                    .createInsecureRfcommSocketToServiceRecord(
                                            SPP_UUID
                                    );

                } catch (Exception e) {

                    last = e;
                }


                try {

                    candidates[1] =
                            printer
                                    .createRfcommSocketToServiceRecord(
                                            SPP_UUID
                                    );

                } catch (Exception e) {

                    last = e;
                }


                try {

                    java.lang.reflect.Method m =
                            printer
                                    .getClass()
                                    .getMethod(
                                            "createRfcommSocket",
                                            int.class
                                    );


                    candidates[2] =
                            (BluetoothSocket)
                                    m.invoke(
                                            printer,
                                            1
                                    );

                } catch (Exception e) {

                    last = e;
                }


                // -------------------------------------------------
                // CONNECT
                // -------------------------------------------------

                for (
                        BluetoothSocket candidate :
                        candidates
                ) {

                    if (candidate == null) {

                        continue;
                    }


                    try {

                        candidate.connect();


                        socket =
                                candidate;


                        break;


                    } catch (Exception e) {

                        last = e;


                        try {

                            candidate.close();

                        } catch (Exception ignored) {
                        }
                    }
                }


                if (
                        socket == null ||
                        !socket.isConnected()
                ) {

                    throw new IOException(
                            "Unable to connect to printer",
                            last
                    );
                }


                // -------------------------------------------------
                // OUTPUT STREAM
                // -------------------------------------------------

                OutputStream out =
                        socket.getOutputStream();


                // Give printer time to finish Bluetooth setup.

                try {

                    Thread.sleep(800);

                } catch (InterruptedException ignored) {

                    Thread.currentThread()
                            .interrupt();
                }


                // -------------------------------------------------
                // INITIALIZE PRINTER
                // -------------------------------------------------

                out.write(
                        new byte[]{
                                0x1B,
                                0x40
                        }
                );


                // -------------------------------------------------
                // RECEIPT HEADER
                // -------------------------------------------------

                out.write(
                        center()
                );


                out.write(
                        text(
                                "HEALTHY FOOD GETS BETTER\n"
                        )
                );


                out.write(
                        text(
                                "GST: 06KOWPS3125E2ZF\n"
                        )
                );


                out.write(
                        text(
                                "FSSAI: 22724926000547\n"
                        )
                );


                out.write(
                        text(
                                "--------------------------------\n"
                        )
                );


                // -------------------------------------------------
                // PRODUCT
                // -------------------------------------------------

                if (
                        !productName.isEmpty()
                ) {

                    out.write(
                            text(
                                    "Product: " +
                                    fit(
                                            safeText(productName),
                                            24
                                    ) +
                                    "\n"
                            )
                    );


                    out.write(
                            text(
                                    "--------------------------------\n"
                            )
                    );
                }


                // -------------------------------------------------
                // AMOUNT
                // -------------------------------------------------

                out.write(
                        leftRight(
                                "Amount:",
                                money(base)
                        )
                );


                out.write(
                        leftRight(
                                "IGST @ 5%:",
                                money(gst)
                        )
                );


                out.write(
                        text(
                                "--------------------------------\n"
                        )
                );


                // -------------------------------------------------
                // TOTAL
                // -------------------------------------------------

                out.write(
                        bold(true)
                );


                out.write(
                        leftRight(
                                "TOTAL PAID:",
                                money(total)
                        )
                );


                out.write(
                        bold(false)
                );


                // -------------------------------------------------
                // PAYMENT
                // -------------------------------------------------

                out.write(
                        text(
                                "PAYMENT: CASH\n"
                        )
                );


                out.write(
                        text(
                                "--------------------------------\n"
                        )
                );


                // -------------------------------------------------
                // FOOTER
                // -------------------------------------------------

                out.write(
                        center()
                );


                out.write(
                        text(
                                "Thank you for the service..!!\n"
                        )
                );


                out.write(
                        text(
                                "Please visit again..!!\n\n\n"
                        )
                );


                // No cutter command.
                // Many low-cost 58mm printers do not
                // have a cutter.


                out.flush();


                try {

                    Thread.sleep(700);

                } catch (InterruptedException ignored) {

                    Thread.currentThread()
                            .interrupt();
                }


                out.close();


                socket.close();


                // -------------------------------------------------
                // SUCCESS
                // -------------------------------------------------

                runOnUiThread(() -> {

                    toast(
                            "Receipt sent to printer."
                    );


                    amount.setText("");

                    product.setText("");

                    amount.requestFocus();
                });


            } catch (Exception e) {


                try {

                    if (socket != null) {

                        socket.close();
                    }

                } catch (Exception ignored) {
                }


                String detail =
                        e.getMessage();


                if (
                        detail == null ||
                        detail.trim().isEmpty()
                ) {

                    detail =
                            e.getClass()
                                    .getSimpleName();
                }


                final String msg =
                        detail;


                runOnUiThread(
                        () -> toast(
                                "Printing failed: " +
                                msg +
                                "\nCheck printer is ON, paired and Bluetooth Classic/ESC-POS is supported."
                        )
                );
            }

        }).start();
    }


    // =========================================================
    // PRINTER TEXT HELPERS
    // =========================================================

    String fit(
            String s,
            int max) {

        return s.length() <= max
                ? s
                : s.substring(0, max);
    }


    String safeText(
            String s) {

        return s
                .replace("₹", "Rs.")
                .replaceAll(
                        "[^\\x20-\\x7E]",
                        "?"
                );
    }


    byte[] center() {

        return new byte[]{
                0x1B,
                0x61,
                0x01
        };
    }


    byte[] bold(
            boolean on) {

        return new byte[]{
                0x1B,
                0x45,
                (byte) (on ? 1 : 0)
        };
    }


    byte[] text(
            String s) {

        return s.getBytes(
                StandardCharsets.UTF_8
        );
    }


    byte[] leftRight(
            String a,
            String b) {

        int width = 32;


        int spaces =
                Math.max(
                        1,
                        width -
                        a.length() -
                        b.length()
                );


        return text(
                a +
                " ".repeat(spaces) +
                b +
                "\n"
        );
    }


    byte[] cut() {

        return new byte[]{
                0x1D,
                0x56,
                0x00
        };
    }


    // =========================================================
    // LOGO GRAYSCALE
    // =========================================================

    ColorMatrix grayMatrix() {

        ColorMatrix m =
                new ColorMatrix();

        m.setSaturation(0);

        return m;
    }


    // =========================================================
    // TOAST
    // =========================================================

    void toast(
            String s) {

        Toast.makeText(
                this,
                s,
                Toast.LENGTH_LONG
        ).show();
    }
}
