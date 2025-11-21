package com.example.backgroundtest

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.bluetooth.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

@SuppressLint("MissingPermission")
class BleConnectionService : Service() {

    // --- Service Properties ---

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val jobScheduler by lazy {
        getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null

    // --- Bluetooth Callbacks ---

    /**
     * Main callback that manages the lifecycle events of the GATT connection.
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothAdapter.STATE_CONNECTED -> {
                    Log.i("BleService", "Successfully connected to device: ${gatt.device.address}")
                    ConnectionManager.updateState(ConnectionState.CONNECTED)

                    // When connected, update the notification to reflect the "Connected" state.
                    updateNotification(
                        "Device Connected",
                        "Active connection with ${gatt.device.address}"
                    )
                }
                BluetoothAdapter.STATE_DISCONNECTED -> {
                    Log.w("BleService", "Disconnected from device: ${gatt.device.address}. Status: $status")
                    ConnectionManager.updateState(ConnectionState.DISCONNECTED)

                    // Update notification to reflect "Reconnecting" state
                    updateNotification(
                        "Device Disconnected",
                        "Attempting to reconnect..."
                    )

                    // IMPORTANT: Instead of closing, we tell the system to try to reconnect automatically.
                    // The OS will listen for the device's advertisement in the background.
                    // This requires the GATT object to have been created with `autoConnect = true`.
                    gatt.connect()
                }
            }
        }
    }

    // --- Service Lifecycle ---

    override fun onBind(intent: Intent): IBinder? = null // We don't use binding, it's a started service.

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val addressFromIntent = intent?.getStringExtra("DEVICE_ADDRESS")

        if (addressFromIntent != null) {
            // If it's a new device, close the old connection and start a new one.
            if (deviceAddress != null && deviceAddress != addressFromIntent) {
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
            deviceAddress = addressFromIntent

            // Store device address in SharedPreferences for JobService recovery
            saveDeviceAddress(addressFromIntent)

            // Promote the service to foreground and start the connection.
            val notification = createNotification(
                "Connecting...",
                "Establishing connection with $deviceAddress"
            )
            startForeground(SERVICE_NOTIFICATION_ID, notification)

            connectToDevice()
            scheduleNotificationWatcher()
        }

        // START_STICKY: If the system kills the service, it will try to recreate it.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("BleService", "Service destroyed. Cleaning up all BLE resources.")
        // Cancel the notification watcher job
        cancelNotificationWatcher()
        // Clear stored device address
        clearDeviceAddress()
        // Stop the foreground service and remove the notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        // Ensure everything is stopped and cleaned up correctly.
        ConnectionManager.updateState(ConnectionState.DISCONNECTING)
        bluetoothGatt?.close()
        bluetoothGatt = null
        ConnectionManager.updateState(ConnectionState.DISCONNECTED)
    }

    // --- Connection Logic ---

    /**
     * Initiates a new GATT connection with the device.
     */
    private fun connectToDevice() {
        deviceAddress?.let { address ->
            ConnectionManager.updateState(ConnectionState.CONNECTING)

            try {
                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
                // Close any previous instance to avoid resource leaks.
                bluetoothGatt?.close()
                // Create a new GATT connection using autoConnect = true for robust reconnection.
                bluetoothGatt = device.connectGatt(this, true, gattCallback)
            } catch (e: IllegalArgumentException) {
                Log.e("BleService", "The MAC address '$address' is not valid.")
                ConnectionManager.updateState(ConnectionState.DISCONNECTED)
                stopSelf() // Stop the service if the address is incorrect.
            }
        }
    }

    // --- Foreground Service Notification ---

    private fun updateNotification(title: String, text: String) {
        val notification = createNotification(title, text)
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }

    /**
     * Creates the persistent notification required for a foreground service.
     */
    private fun createNotification(title: String, text: String): Notification {
        val channelId = "ble_connection_channel"
        // The notification channel is mandatory from Android 8.0 (Oreo).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "BLE Connection", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Notifications for BLE connection status"
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher) // You should use a more suitable icon.
            .setOngoing(true) // Makes the notification non-dismissable by the user.
            .build()
    }

    // --- JobScheduler for Notification Monitoring ---

    /**
     * Schedules a periodic job to check if the notification is still active every 15 minutes.
     * JobScheduler is more resilient than coroutines and survives app restarts.
     * Note: 15 minutes is the minimum interval allowed by Android for periodic jobs.
     */
    private fun scheduleNotificationWatcher() {
        val componentName = ComponentName(this, NotificationWatcherJob::class.java)
        val jobInfo = JobInfo.Builder(NotificationWatcherJob.JOB_ID, componentName)
            .setPeriodic(15 * 60 * 1000) // Run every 15 minutes (minimum allowed by Android)
            .setPersisted(true) // Persist across device reboots
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE) // No network required
            .build()

        val result = jobScheduler.schedule(jobInfo)
        if (result == JobScheduler.RESULT_SUCCESS) {
            Log.i("BleService", "Notification watcher job scheduled successfully (every 15 minutes)")
        } else {
            Log.e("BleService", "Failed to schedule notification watcher job")
        }
    }

    /**
     * Cancels the notification watcher job when the service is destroyed.
     */
    private fun cancelNotificationWatcher() {
        jobScheduler.cancel(NotificationWatcherJob.JOB_ID)
        Log.i("BleService", "Notification watcher job cancelled")
    }

    /**
     * Saves the device address to SharedPreferences so the JobService can recover it.
     */
    private fun saveDeviceAddress(address: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE_ADDRESS, address)
            .apply()
    }

    /**
     * Clears the device address from SharedPreferences when service is destroyed.
     */
    private fun clearDeviceAddress() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DEVICE_ADDRESS)
            .apply()
    }

    companion object {
        const val SERVICE_NOTIFICATION_ID = 1
        private const val PREFS_NAME = "BleConnectionPrefs"
        private const val KEY_DEVICE_ADDRESS = "device_address"
    }
}
