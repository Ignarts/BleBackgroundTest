package com.example.backgroundtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
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
import com.example.backgroundtest.ui.theme.BackgroundTestTheme

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    // --- Adaptador y Scanner Bluetooth ---
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    // --- Estado de la UI y Lógica de Escaneo ---
    private val discoveredDevices = mutableStateListOf<BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private val SCAN_PERIOD: Long = 10000 // Escaneará durante 10 segundos

    // --- Callbacks de Bluetooth ---
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            // LOG para ver todo lo que llega
            Log.v("BleScanner-RAW", "Dispositivo detectado: ${result.device.address}, Nombre: ${result.device.name}")

            val device = result.device
            // Solo procesamos dispositivos que tengan un nombre.
            if (device.name == null) {
                return
            }

            runOnUiThread {
                val existingDeviceIndex = discoveredDevices.indexOfFirst { it.address == device.address }

                if (existingDeviceIndex == -1) {
                    // Dispositivo nuevo con nombre, lo añadimos a la lista.
                    Log.i("BleScanner-UI", "Añadiendo nuevo dispositivo: ${device.name}")
                    discoveredDevices.add(device)
                }
                // Ya no necesitamos la lógica 'else' porque si el dispositivo ya está,
                // significa que ya tenía nombre. No hacemos nada.
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScanner", "El escaneo BLE falló con código: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothAdapter.STATE_CONNECTED) {
                Log.i("BluetoothGattCallback", "Conectado al dispositivo ${gatt?.device?.name}")
                // Una vez conectado, puedes interactuar con los servicios del dispositivo
                // Por ahora, solo nos desconectamos para el ejemplo.
                gatt?.disconnect()
            }
        }
    }

    // --- Gestión de Permisos y Solicitudes ---
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                // Si los permisos se conceden, intenta iniciar el escaneo
                scanLeDevice()
            } else {
                // Informar al usuario que los permisos son necesarios
                Log.w("Permissions", "No se concedieron todos los permisos de Bluetooth/Ubicación.")
            }
        }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("Bluetooth", "El usuario habilitó Bluetooth. Iniciando escaneo.")
            scanLeDevice()
        } else {
            Log.w("Bluetooth", "El usuario no habilitó Bluetooth.")
        }
    }

    // --- Ciclo de Vida de la Activity ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val rememberedDevices = remember { discoveredDevices }

            BackgroundTestTheme {
                DeviceListScreen(
                    devices = rememberedDevices,
                    onDeviceClick = { device ->
                        connectToDevice(device)
                    },
                    onScanClick = {
                        if (hasRequiredPermissions()) {
                            scanLeDevice() // Inicia el ciclo de escaneo
                        } else {
                            requestBlePermissions()
                        }
                    }
                )
            }
        }
    }

    // --- Funciones de Control ---

    private fun scanLeDevice() {
        // Primero, comprueba si el Bluetooth está habilitado
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return // La función se volverá a llamar si el usuario activa el Bluetooth
        }

        if (!isScanning) {
            // Detiene el escaneo automáticamente después del período definido
            handler.postDelayed({
                isScanning = false
                bleScanner?.stopScan(scanCallback)
                Log.i("BleScanner", "Escaneo detenido automáticamente.")
            }, SCAN_PERIOD)

            // Inicia el escaneo
            isScanning = true
            // NOTA: Ya no se limpia la lista para mantener los dispositivos entre escaneos
            // discoveredDevices.clear() 
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bleScanner?.startScan(null, scanSettings, scanCallback)
            Log.i("BleScanner", "Iniciando escaneo de 10 segundos...")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (isScanning) {
            isScanning = false
            bleScanner?.stopScan(scanCallback)
        }
        Log.i("BleConnection", "Conectando a ${device.name}...")
        device.connectGatt(this, false, gattCallback)
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No se requiere permiso explícito de escaneo antes de Android S
        }

        val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No se requiere permiso explícito de conexión antes de Android S
        }

        val hasLocationPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // El permiso de ubicación ya no es estrictamente para BLE a partir de Android S
        }

        return hasScanPermission && hasConnectPermission && hasLocationPermission
    }

    private fun requestBlePermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Para Android 11 (R) y anteriores, se necesita ACCESS_FINE_LOCATION
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissionsLauncher.launch(permissionsToRequest)
    }
}
