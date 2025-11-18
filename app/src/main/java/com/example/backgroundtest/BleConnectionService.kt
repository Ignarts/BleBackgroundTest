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

@SuppressLint("MissingPermission") // Ensure you have permissions in the Manifest
class BleConnectionService : Service() {

    private val binder = LocalBinder()
    private var bluetoothGatt: BluetoothGatt? = null
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
            deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")

            if (deviceAddress == null) {
                Log.w("BleService", "No address provided. Stopping service.")
                stopSelf()
                return START_NOT_STICKY
            }

            startForeground(NOTIFICATION_ID, createNotification("Connecting...", "Connecting to $deviceAddress"))
            Log.d("BleService", "Attempting to connect to $deviceAddress")

            // Stop any passive scan that might be active, since we are now attempting a direct connection.
            BleScanManager.stopBackgroundScan(this)

            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = bluetoothManager.adapter.getRemoteDevice(deviceAddress)

            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(this, false, gattCallback) // false, for direct connection attempt
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BleService", "Service destroyed. Cleaning up GATT.")
        bluetoothGatt?.close()
        bluetoothGatt = null
        stopForeground(true)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            deviceAddress = gatt.device.address // Make sure we have the address for reconnection
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("GattCallback", "Connected to device: ${gatt.device.address}")
                    ConnectionManager.updateState(ConnectionState.CONNECTED)
                    updateNotification("Device Connected", "Active connection with ${gatt.device.name ?: gatt.device.address}")
                    // Stop any background scan that might be running
                    BleScanManager.stopBackgroundScan(this@BleConnectionService)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w("GattCallback", "Disconnected from device: ${gatt.device.address}")
                    ConnectionManager.updateState(ConnectionState.DISCONNECTED)
                    // 1. Delegate the search to the OS.
                    deviceAddress?.let { BleScanManager.startBackgroundScan(this@BleConnectionService, it) }
                    // 2. Stop the service to save battery. The BroadcastReceiver will restart it.
                    stopSelf()
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
        val icon = R.mipmap.ic_launcher
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
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
