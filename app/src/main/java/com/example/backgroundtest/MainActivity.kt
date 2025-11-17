package com.example.backgroundtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.backgroundtest.ui.theme.BackgroundTestTheme

// Main activity for the app
@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    // Bluetooth adapter and scanner
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }
    private val discoveredDevices = mutableStateListOf<BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private val SCAN_PERIOD: Long = 10000

    // Scan function to find BLE devices
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            if (device.name == null) return

            runOnUiThread {
                if (discoveredDevices.none { it.address == device.address }) {
                    discoveredDevices.add(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScanner", "El escaneo BLE falló con código: $errorCode")
        }
    }

    // Activity result launcher for handling Bluetooth permissions
    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            scanLeDevice()
        } else {
            Log.w("Permissions", "No se concedieron todos los permisos.")
        }
    }

    // Activity result launcher for enabling Bluetooth
    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scanLeDevice()
        } else {
            Log.w("Bluetooth", "El usuario no habilitó Bluetooth.")
        }
    }

    // Activity creation
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            BackgroundTestTheme {
                NavHost(navController = navController, startDestination = "device_list") {
                    composable("device_list") {
                        DeviceListScreen(
                            devices = discoveredDevices,
                            onDeviceClick = { device ->
                                val intent = Intent(this@MainActivity, BleConnectionService::class.java).apply {
                                    putExtra("DEVICE_ADDRESS", device.address)
                                }
                                startService(intent)
                                navController.navigate("device_detail/${device.address}/${device.name}")
                            },
                            onScanClick = { if (hasRequiredPermissions()) scanLeDevice() else requestAppPermissions() }
                        )
                    }
                    composable("device_detail/{deviceAddress}/{deviceName}") { backStackEntry ->
                        val deviceName = backStackEntry.arguments?.getString("deviceName")
                        DeviceDetailScreen(
                            deviceName = deviceName,
                            onDisconnect = {
                                val intent = Intent(this@MainActivity, BleConnectionService::class.java)
                                stopService(intent)
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    // Start scanning for BLE devices
    private fun scanLeDevice() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        if (!isScanning) {
            handler.postDelayed({ isScanning = false; bleScanner?.stopScan(scanCallback) }, SCAN_PERIOD)
            isScanning = true
            bleScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        }
    }

    // Check for required permissions
    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Request all necessary permissions
    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
