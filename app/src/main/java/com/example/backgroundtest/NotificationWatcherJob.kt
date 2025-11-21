package com.example.backgroundtest

import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * JobService that periodically checks if the BLE service notification is still active.
 * If the notification has been dismissed, it restarts the foreground service to restore it.
 *
 * This runs independently of the app's lifecycle, making it resilient to the OS killing the app.
 */
class NotificationWatcherJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("NotificationWatcher", "Job started - checking notification status")

        // Check if notification is active
        if (!isNotificationActive()) {
            Log.w("NotificationWatcher", "Notification is not active, restarting service")
            restartForegroundService()
        } else {
            Log.d("NotificationWatcher", "Notification is active")
        }

        // Return false because we finished the work synchronously
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d("NotificationWatcher", "Job stopped by system")
        // Return true to reschedule the job if it was stopped prematurely
        return true
    }

    /**
     * Checks if the BLE service notification is currently active.
     */
    private fun isNotificationActive(): Boolean {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For API 23+, we can query active notifications
            val activeNotifications = notificationManager.activeNotifications
            activeNotifications.any { it.id == SERVICE_NOTIFICATION_ID }
        } else {
            // For older versions, assume it's active
            true
        }
    }

    /**
     * Restarts the foreground service to restore the notification.
     * This will only work if we have a stored device address.
     */
    private fun restartForegroundService() {
        // Check if we have connection info stored in SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceAddress = prefs.getString(KEY_DEVICE_ADDRESS, null)

        if (deviceAddress != null) {
            Log.i("NotificationWatcher", "Restarting service for device: $deviceAddress")
            val serviceIntent = android.content.Intent(this, BleConnectionService::class.java).apply {
                putExtra("DEVICE_ADDRESS", deviceAddress)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            Log.w("NotificationWatcher", "No device address stored, cannot restart service")
        }
    }

    companion object {
        const val JOB_ID = 1001
        private const val SERVICE_NOTIFICATION_ID = 1
        private const val PREFS_NAME = "BleConnectionPrefs"
        private const val KEY_DEVICE_ADDRESS = "device_address"
    }
}
