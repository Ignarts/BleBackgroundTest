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
    private val handler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null
    private var isScanningForReconnect = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.i("BleService", "Dispositivo para reconexión encontrado. Conectando...")
            stopReconnectionScan()
            connectToDevice()
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleService", "Fallo en el escaneo de reconexión: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothAdapter.STATE_CONNECTED -> {
                    Log.i("BleService", "Conectado al dispositivo: ${gatt.device.address}")
                    if (isScanningForReconnect) {
                        stopReconnectionScan()
                    }
                    ConnectionManager.updateState(ConnectionState.CONNECTED)
                }
                BluetoothAdapter.STATE_DISCONNECTED -> {
                    Log.w("BleService", "Desconectado del dispositivo: ${gatt.device.address}")
                    ConnectionManager.updateState(ConnectionState.DISCONNECTED)
                    gatt.close()
                    bluetoothGatt = null
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
        if (isScanningForReconnect || deviceAddress == null) return
        isScanningForReconnect = true
        Log.i("BleService", "Iniciando búsqueda para reconexión automática...")

        val scanFilters = listOf(ScanFilter.Builder().setDeviceAddress(deviceAddress).build())
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()

        handler.post(object : Runnable {
            override fun run() {
                if (!isScanningForReconnect) return

                Log.d("BleService", "Buscando dispositivo $deviceAddress...")
                leScanner.startScan(scanFilters, scanSettings, scanCallback)

                handler.postDelayed({
                    if (isScanningForReconnect) {
                        leScanner.stopScan(scanCallback)
                    }
                }, 2000)

                handler.postDelayed(this, 5000)
            }
        })
    }

    private fun stopReconnectionScan() {
        if (!isScanningForReconnect) return
        Log.i("BleService", "Deteniendo búsqueda para reconexión.")
        isScanningForReconnect = false
        leScanner.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
    }

    private fun connectToDevice() {
        stopReconnectionScan()
        deviceAddress?.let { address ->
            ConnectionManager.updateState(ConnectionState.CONNECTING)
            try {
                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
                bluetoothGatt?.close()
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
