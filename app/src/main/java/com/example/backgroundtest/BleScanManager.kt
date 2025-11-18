package com.example.backgroundtest

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService

object BleScanManager {

    private const val TAG = "BleScanManager"
    private const val PENDING_INTENT_REQUEST_CODE = 2 // Using a different code to ensure uniqueness

    @SuppressLint("MissingPermission")
    fun startBackgroundScan(context: Context, deviceAddress: String) {
        val bluetoothManager = context.getSystemService<BluetoothManager>()
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not available or not enabled.")
            return
        }

        val scanFilter = ScanFilter.Builder()
            .setDeviceAddress(deviceAddress)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .build()

        val pendingIntent = createScanPendingIntent(context)

        Log.i(TAG, "Registering background scan with PendingIntent for device $deviceAddress")
        // Stop any existing scan before starting a new one to be safe
        try {
            bluetoothAdapter.bluetoothLeScanner.stopScan(pendingIntent)
        } catch (e: Exception) { /* Ignore errors if scan wasn't running */ }
        bluetoothAdapter.bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, pendingIntent)
    }

    @SuppressLint("MissingPermission")
    fun stopBackgroundScan(context: Context) {
        val bluetoothManager = context.getSystemService<BluetoothManager>()
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not available or not enabled when trying to stop scan.")
            return
        }
        val pendingIntent = createScanPendingIntent(context)

        Log.i(TAG, "Stopping background scan.")
        try {
            bluetoothAdapter.bluetoothLeScanner.stopScan(pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background scan: ${e.message}")
        }
    }

    private fun createScanPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BleScanReceiver::class.java).apply {
            action = "com.example.backgroundtest.ACTION_BLE_SCAN_RESULT"
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context.applicationContext, // Use application context for broader lifecycle
            PENDING_INTENT_REQUEST_CODE,
            intent,
            flags
        )
    }
}