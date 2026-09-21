# Healthy Food Cash Receipt — Android

Simple Android app for a 58 mm Bluetooth Classic SPP / ESC-POS thermal printer.

## Workflow
1. Pair the printer in Android Bluetooth settings.
2. In the app, tap SELECT PRINTER once.
3. Enter an optional Product / Service name.
4. Enter only the TOTAL amount paid by the customer.
5. Tap PRINT CASH RECEIPT.
6. The selected printer is remembered, so future receipts require only product (optional) + amount + PRINT.

## Tax calculation
The entered amount is the customer TOTAL and is treated as inclusive of 5% IGST.
Base amount = Total / 1.05
IGST = Total - Base amount

## Receipt
HEALTHY FOOD GETS BETTER
GST: 06KOWPS3125E2ZF
FSSAI: 22724926000547
Optional product name
Amount before IGST
IGST @ 5%
TOTAL PAID
PAYMENT: CASH
Thank you for the service..!!
Please visit again..!!

## Compatibility
The printer should support Bluetooth Classic SPP and ESC/POS commands, which is common for mini 58 mm thermal printers.

Minimum Android: 8.0 (API 26)
Target SDK: 35


## MPT-II verification
The MPT-II was verified with a Windows laptop using Bluetooth Classic SPP on the outgoing serial connection (COM4). A raw ESC/POS test receipt printed successfully. The Android printer code therefore prioritizes the insecure SPP connection, then secure SPP, then RFCOMM channel 1, with a connection settling delay and ASCII-safe ESC/POS output.
