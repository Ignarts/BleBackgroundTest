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
    private var servicesDiscovered = false  // Flag to prevent multiple discoveries
    private val characteristicDiscoveryService = BluetoothCharacteristicDiscoveryService()

    companion object {
        const val SERVICE_NOTIFICATION_ID = 1
        private const val PREFS_NAME = "BleConnectionPrefs"
        private const val KEY_DEVICE_ADDRESS = "device_address"

        // Descriptor UUID for enabling notifications/indications
        private val CLIENT_CHARACTERISTIC_CONFIG = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Singleton instance reference for accessing service methods
        @Volatile
        private var instance: BleConnectionService? = null

        fun getInstance(): BleConnectionService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
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

                    // Start service discovery to find available characteristics (only once per connection)
                    if (!servicesDiscovered) {
                        Log.i("BleService", "Starting service discovery...")
                        servicesDiscovered = true
                        gatt.discoverServices()
                    } else {
                        Log.i("BleService", "Services already discovered, skipping...")
                    }
                }
                BluetoothAdapter.STATE_DISCONNECTED -> {
                    Log.w("BleService", "Disconnected from device: ${gatt.device.address}. Status: $status")
                    ConnectionManager.updateState(ConnectionState.DISCONNECTED)

                    // Reset flag so services are rediscovered on reconnection
                    servicesDiscovered = false

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

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BleService", "=== Services discovered successfully ===")
                val characteristicsMap = mutableMapOf<String, BleCharacteristic>()

                gatt.services?.forEachIndexed { serviceIndex, service ->
                    Log.i("BleService", "Service #$serviceIndex: ${service.uuid}")
                    service.characteristics?.forEachIndexed { charIndex, characteristic ->
                        val charUuid = characteristic.uuid.toString()
                        val bleChar = BleCharacteristic(
                            uuid = charUuid,
                            serviceUuid = service.uuid.toString(),
                            canRead = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0,
                            canWrite = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0,
                            canNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0,
                            canIndicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                        )

                        // Solo añadir si no existe, o combinar propiedades si ya existe
                        if (characteristicsMap.containsKey(charUuid)) {
                            val existing = characteristicsMap[charUuid]!!
                            // Combinar propiedades: si alguna versión tiene la propiedad, la mantenemos
                            characteristicsMap[charUuid] = existing.copy(
                                canRead = existing.canRead || bleChar.canRead,
                                canWrite = existing.canWrite || bleChar.canWrite,
                                canNotify = existing.canNotify || bleChar.canNotify,
                                canIndicate = existing.canIndicate || bleChar.canIndicate
                            )
                            Log.i("BleService", "  Char #$charIndex: ${characteristic.uuid} - R:${bleChar.canRead} W:${bleChar.canWrite} N:${bleChar.canNotify} (MERGED with existing)")
                        } else {
                            characteristicsMap[charUuid] = bleChar
                            Log.i("BleService", "  Char #$charIndex: ${characteristic.uuid} - R:${bleChar.canRead} W:${bleChar.canWrite} N:${bleChar.canNotify} (NEW)")
                        }
                    }
                }

                val uniqueCharacteristics = characteristicsMap.values.toList()
                Log.i("BleService", "=== Total unique characteristics: ${uniqueCharacteristics.size} ===")
                ConnectionManager.updateDiscoveredCharacteristics(uniqueCharacteristics)

                // Intentar descubrir el par de características ideal para OBD-II
                try {
                    val pair = characteristicDiscoveryService.discover(gatt)
                    ConnectionManager.updateDiscoveredPair(pair)
                    Log.i("BleService", "✓ Auto-discovered characteristic pair for OBD-II communication")
                } catch (e: BluetoothCharacteristicsNotFoundError) {
                    // Intentar búsqueda entre servicios
                    try {
                        val pair = characteristicDiscoveryService.discoverAcrossServices(gatt)
                        ConnectionManager.updateDiscoveredPair(pair)
                        Log.i("BleService", "✓ Auto-discovered characteristic pair across services")
                    } catch (e2: BluetoothCharacteristicsNotFoundError) {
                        Log.w("BleService", "Could not auto-discover characteristic pair. Manual selection required.")
                        ConnectionManager.updateDiscoveredPair(null)
                    }
                }
            } else {
                Log.e("BleService", "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BleService", "Characteristic read: ${characteristic.uuid}, data: ${value.contentToString()}")
                ConnectionManager.updateCharacteristicData(
                    CharacteristicData(
                        characteristicUuid = characteristic.uuid.toString(),
                        data = value
                    )
                )
            } else {
                Log.e("BleService", "Failed to read characteristic: ${characteristic.uuid}, status: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BleService", "Characteristic written successfully: ${characteristic.uuid}")
            } else {
                Log.e("BleService", "Failed to write characteristic: ${characteristic.uuid}, status: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.i("BleService", "Characteristic changed: ${characteristic.uuid}, data: ${value.contentToString()}")
            ConnectionManager.updateCharacteristicData(
                CharacteristicData(
                    characteristicUuid = characteristic.uuid.toString(),
                    data = value
                )
            )
        }
    }

    // --- Service Lifecycle ---

    override fun onBind(intent: Intent): IBinder? = null // We don't use binding, it's a started service.

    // --- Public Methods for BLE Operations ---

    /**
     * Reads a characteristic value from the connected device.
     * @param characteristicUuid The UUID of the characteristic to read.
     * @return true if the read operation was initiated successfully.
     */
    fun readCharacteristic(characteristicUuid: String): Boolean {
        val gatt = bluetoothGatt ?: run {
            Log.e("BleService", "Cannot read characteristic: GATT is null")
            return false
        }

        val characteristic = findCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.e("BleService", "Characteristic not found: $characteristicUuid")
            return false
        }

        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
            Log.e("BleService", "Characteristic does not support read: $characteristicUuid")
            return false
        }

        return gatt.readCharacteristic(characteristic)
    }

    /**
     * Writes data to a characteristic.
     * @param characteristicUuid The UUID of the characteristic to write to.
     * @param data The data to write.
     * @return true if the write operation was initiated successfully.
     */
    fun writeCharacteristic(characteristicUuid: String, data: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: run {
            Log.e("BleService", "Cannot write characteristic: GATT is null")
            return false
        }

        val characteristic = findCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.e("BleService", "Characteristic not found: $characteristicUuid")
            return false
        }

        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 &&
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0) {
            Log.e("BleService", "Characteristic does not support write: $characteristicUuid")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+, use the new API
            val writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            val result = gatt.writeCharacteristic(characteristic, data, writeType)
            result == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            // For older Android versions, use deprecated API
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    /**
     * Enables or disables notifications for a characteristic.
     * @param characteristicUuid The UUID of the characteristic.
     * @param enable true to enable notifications, false to disable.
     * @return true if the operation was initiated successfully.
     */
    fun setCharacteristicNotification(characteristicUuid: String, enable: Boolean): Boolean {
        val gatt = bluetoothGatt ?: run {
            Log.e("BleService", "Cannot set notification: GATT is null")
            return false
        }

        val characteristic = findCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.e("BleService", "Characteristic not found: $characteristicUuid")
            return false
        }

        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 &&
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE == 0) {
            Log.e("BleService", "Characteristic does not support notifications/indications: $characteristicUuid")
            return false
        }

        // Enable/disable local notifications
        if (!gatt.setCharacteristicNotification(characteristic, enable)) {
            Log.e("BleService", "Failed to set characteristic notification")
            return false
        }

        // Write to the Client Characteristic Configuration Descriptor
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor == null) {
            Log.e("BleService", "CCCD not found for characteristic: $characteristicUuid")
            return false
        }

        val value = if (enable) {
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeDescriptor(descriptor, value)
            result == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    /**
     * Finds a characteristic by UUID across all services.
     */
    private fun findCharacteristic(characteristicUuid: String): BluetoothGattCharacteristic? {
        val uuid = try {
            java.util.UUID.fromString(characteristicUuid)
        } catch (e: IllegalArgumentException) {
            Log.e("BleService", "Invalid UUID format: $characteristicUuid")
            return null
        }

        bluetoothGatt?.services?.forEach { service ->
            service.characteristics?.forEach { characteristic ->
                if (characteristic.uuid == uuid) {
                    return characteristic
                }
            }
        }
        return null
    }

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
                // Reset the services discovered flag
                servicesDiscovered = false
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
}
