package com.example.backgroundtest

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

@SuppressLint("MissingPermission")
class BleScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // A scan result has been received from the system.
        if (intent.action == "com.example.backgroundtest.ACTION_BLE_SCAN_RESULT") {

            val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
            if (errorCode != -1) {
                Log.e("BleScanReceiver", "Scan failed with error code: $errorCode")
                return
            }

            val scanResults: ArrayList<ScanResult>? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
            }

            if (scanResults.isNullOrEmpty()) {
                Log.w("BleScanReceiver", "No scan results found in intent.")
                return
            }

            // The system usually delivers a single result if configured correctly
            val scanResult = scanResults[0]
            Log.i("BleScanReceiver", "Device found by the system!: ${scanResult.device.address}")

            // NOW, start the ForegroundService to connect
            val serviceIntent = Intent(context, BleConnectionService::class.java).apply {
                putExtra("DEVICE_ADDRESS", scanResult.device.address)
                action = BleConnectionService.ACTION_CONNECT
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}