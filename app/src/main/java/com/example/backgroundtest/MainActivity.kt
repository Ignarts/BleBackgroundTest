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
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.backgroundtest.ui.theme.BackgroundTestTheme

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }
    private val discoveredDevices = mutableStateListOf<BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private val SCAN_PERIOD: Long = 10000

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

    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) scanLeDevice()
        else Log.w("Permissions", "No se concedieron todos los permisos.")
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) scanLeDevice()
        else Log.w("Bluetooth", "El usuario no habilitó Bluetooth.")
    }

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
                            onScanClick = { if (hasRequiredPermissions()) scanLeDevice() else requestBlePermissions() }
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

    private fun hasRequiredPermissions(): Boolean {
        val hasScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED else true
        val hasConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED else true
        val hasLocation = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED else true
        return hasScan && hasConnect && hasLocation
    }

    private fun requestBlePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissionsLauncher.launch(permissions)
    }
}