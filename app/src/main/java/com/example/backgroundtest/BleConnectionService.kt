package com.example.backgroundtest

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

@SuppressLint("MissingPermission")
class BleConnectionService : Service() {

    // --- Service Properties ---

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null

    // --- Bluetooth Callbacks ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothAdapter.STATE_CONNECTED -> {
                    Log.i("BleService", "Successfully connected to device: ${gatt.device.address}")
                    ConnectionManager.updateState(ConnectionState.CONNECTED)
                }
                BluetoothAdapter.STATE_DISCONNECTED -> {
                    Log.w("BleService", "Disconnected from device: ${gatt.device.address}. Status: $status")
                    ConnectionManager.updateState(ConnectionState.DISCONNECTED)
                    // The OS will try to reconnect automatically since the GATT object was created with `autoConnect = true`.
                    gatt.connect()
                }
            }
        }
    }

    // --- Service Lifecycle ---

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val addressFromIntent = intent?.getStringExtra("DEVICE_ADDRESS")

        if (addressFromIntent != null) {
            if (deviceAddress != null && deviceAddress != addressFromIntent) {
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
            deviceAddress = addressFromIntent
            connectToDevice()
        }

        // We don't want the service to be restarted if killed by the system.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("BleService", "Service destroyed. Cleaning up all BLE resources.")
        ConnectionManager.updateState(ConnectionState.DISCONNECTING)
        bluetoothGatt?.close()
        bluetoothGatt = null
        ConnectionManager.updateState(ConnectionState.DISCONNECTED)
    }

    // --- Connection Logic ---

    private fun connectToDevice() {
        deviceAddress?.let { address ->
            ConnectionManager.updateState(ConnectionState.CONNECTING)

            try {
                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
                bluetoothGatt?.close()
                bluetoothGatt = device.connectGatt(this, true, gattCallback)
            } catch (e: IllegalArgumentException) {
                Log.e("BleService", "The MAC address '$address' is not valid.")
                ConnectionManager.updateState(ConnectionState.DISCONNECTED)
                stopSelf()
            }
        }
    }
}
