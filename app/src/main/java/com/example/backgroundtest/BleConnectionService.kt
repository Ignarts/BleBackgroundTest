package com.example.backgroundtest

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

@SuppressLint("MissingPermission")
class BleConnectionService : Service() {
    @Volatile private var isEverConnected = false

    private val binder = LocalBinder()
    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var notificationManager: NotificationManager
    private var deviceAddress: String? = null

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "BleConnectionChannel"
        private const val NOTIFICATION_ID = 101
        const val ACTION_CONNECT = "com.example.backgroundtest.ACTION_CONNECT"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONNECT) {
            val newDeviceAddress = intent.getStringExtra("DEVICE_ADDRESS")

            if (newDeviceAddress == null) {
                Log.w("BleService", "No address provided. Stopping service.")
                stopSelf()
                return START_NOT_STICKY
            }
            deviceAddress = newDeviceAddress

            startForeground(NOTIFICATION_ID, createNotification("Connecting...", "Connecting to $deviceAddress"))
            Log.d("BleService", "Attempting to connect to $deviceAddress")

            ConnectionManager.stopReconnectionScan(this)

            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = bluetoothManager.adapter.getRemoteDevice(deviceAddress)

            ConnectionManager.updateState(ConnectionState.CONNECTING, deviceAddress)

            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BleService", "Service destroyed. Cleaning up GATT.")
        ConnectionManager.stopReconnectionScan(this)
        bluetoothGatt?.close()
        bluetoothGatt = null
        stopForeground(true)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt != bluetoothGatt) {
                Log.w("GattCallback", "Callback received for a stale GATT instance. Ignoring.")
                return
            }

            val address = gatt.device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("GattCallback", "Connected to device: $address")
                    ConnectionManager.updateState(ConnectionState.CONNECTED, address)
                    updateNotification("Device Connected", "Active connection with ${gatt.device.name ?: address}")
                    ConnectionManager.stopReconnectionScan(this@BleConnectionService)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w("GattCallback", "Disconnected from device: $address, status: $status")
                    gatt.close()
                    bluetoothGatt = null

                    updateNotification("Device Disconnected", "Attempting to reconnect...")
                    ConnectionManager.onConnectionLost(this@BleConnectionService)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "BLE Connection"
            val descriptionText = "Notifications about the BLE connection status"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notification = createNotification(title, text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleConnectionService = this@BleConnectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
