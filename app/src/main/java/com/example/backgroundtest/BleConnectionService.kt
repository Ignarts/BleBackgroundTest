package com.example.backgroundtest

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

@SuppressLint("MissingPermission")
class BleConnectionService : Service() {

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val leScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private val handler = Handler(Looper.getMainLooper()) // Handler can still be useful for other tasks

    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null
    private var isAttemptingReconnect = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // This is our event! The OS found the device.
            Log.i("BleService", "Dispositivo para reconexión encontrado. Conectando...")
            // The scan stops automatically on first match with CALLBACK_TYPE_FIRST_MATCH,
            // but we call stop just to be sure and to clean up our state.
            stopReconnectionScan()
            connectToDevice()
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleService", "Fallo en el escaneo de reconexión: $errorCode")
            // If scan fails, maybe retry after a delay, but for now we just log it.
            isAttemptingReconnect = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothAdapter.STATE_CONNECTED -> {
                    Log.i("BleService", "Conectado al dispositivo: ${gatt.device.address}")
                    isAttemptingReconnect = false // Successfully reconnected
                    ConnectionManager.updateState(ConnectionState.CONNECTED)
                }
                BluetoothAdapter.STATE_DISCONNECTED -> {
                    Log.w("BleService", "Desconectado del dispositivo: ${gatt.device.address}")
                    ConnectionManager.updateState(ConnectionState.DISCONNECTED)
                    gatt.close()
                    bluetoothGatt = null
                    // Start the event-driven reconnection process
                    startReconnectionScan()
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newDeviceAddress = intent?.getStringExtra("DEVICE_ADDRESS")
        if (newDeviceAddress != null && newDeviceAddress != deviceAddress) {
            deviceAddress = newDeviceAddress
            connectToDevice()
        }

        startForeground(1, createNotification())
        return START_STICKY
    }

    private fun startReconnectionScan() {
        if (isAttemptingReconnect || deviceAddress == null) return
        isAttemptingReconnect = true
        Log.i("BleService", "Iniciando búsqueda de reconexión basada en eventos...")

        val scanFilters = listOf(ScanFilter.Builder().setDeviceAddress(deviceAddress).build())
        
        // This is the key change: CALLBACK_TYPE_FIRST_MATCH
        // We tell the OS to notify us only the first time it sees our device.
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED) // Use low power for long-running background scans
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        leScanner.startScan(scanFilters, scanSettings, scanCallback)
    }

    private fun stopReconnectionScan() {
        if (!isAttemptingReconnect) return
        Log.i("BleService", "Deteniendo búsqueda para reconexión.")
        isAttemptingReconnect = false
        leScanner.stopScan(scanCallback)
    }

    private fun connectToDevice() {
        if(isAttemptingReconnect) {
            stopReconnectionScan()
        }
        deviceAddress?.let { address ->
            ConnectionManager.updateState(ConnectionState.CONNECTING)
            try {
                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
                // Close previous gatt instance if it exists
                bluetoothGatt?.close()
                // Connect with a new gatt instance
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
            } catch (e: IllegalArgumentException) {
                Log.e("BleService", "Dirección de dispositivo no válida.")
                ConnectionManager.updateState(ConnectionState.DISCONNECTED)
                stopSelf()
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "ble_connection_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Conexión BLE", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Servicio BLE Activo")
            .setContentText("Manteniendo la conexión con el dispositivo BLE en segundo plano.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("BleService", "Servicio destruido. Desconectando GATT.")
        stopReconnectionScan()
        ConnectionManager.updateState(ConnectionState.DISCONNECTING)
        bluetoothGatt?.close()
        bluetoothGatt = null
        ConnectionManager.updateState(ConnectionState.DISCONNECTED)
    }
}
