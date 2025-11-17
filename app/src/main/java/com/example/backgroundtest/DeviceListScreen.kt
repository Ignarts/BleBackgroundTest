// FICHERO: DeviceListScreen.kt
// DESCRIPCIÓN: Define la interfaz de usuario para mostrar la lista de dispositivos BLE encontrados.

package com.example.backgroundtest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pantalla principal que muestra la lista de dispositivos BLE descubiertos y un botón para escanear.
 *
 * @param devices Lista de dispositivos Bluetooth (`BluetoothDevice`) para mostrar.
 * @param onDeviceClick Lambda que se ejecuta cuando el usuario pulsa sobre un dispositivo de la lista.
 * @param onScanClick Lambda que se ejecuta cuando el usuario pulsa el botón de escanear.
 */
@SuppressLint("MissingPermission")
@Composable
fun DeviceListScreen(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onScanClick: () -> Unit
) {
    // Contenedor principal que organiza los elementos verticalmente.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 25.dp, vertical = 40.dp)
    ) {
        // --- SECCIÓN DE CONTROL DE ESCANEO ---
        Text(
            "Control de Escaneo",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF007BFF),
                contentColor = Color.White
            )
        ) {
            Text("Escanear Dispositivos BLE", fontSize = 16.sp)
        }

        // Espacio para separar visualmente el botón de la lista de dispositivos.
        Spacer(modifier = Modifier.height(24.dp))

        // --- SECCIÓN DE LA LISTA DE DISPOSITIVOS ---
        Text(
            "Dispositivos Encontrados",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        // `LazyColumn` es el equivalente a RecyclerView en Compose.
        // Es eficiente para mostrar listas largas sin consumir toda la memoria.
        LazyColumn {
            items(devices) { device ->
                DeviceItem(device = device, onDeviceClick = onDeviceClick)
            }
        }
    }
}

/**
 * Representa un único elemento (una tarjeta) en la lista de dispositivos.
 *
 * @param device El objeto `BluetoothDevice` que se va a mostrar.
 * @param onDeviceClick Lambda que se ejecuta al hacer clic en la tarjeta.
 */
@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onDeviceClick: (BluetoothDevice) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onDeviceClick(device) }, // Permite hacer clic en toda la tarjeta.
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Muestra el nombre del dispositivo. Si es nulo, muestra la dirección MAC.
            // Esto asegura que siempre veamos una identificación para cada dispositivo.
            Text(
                text = device.name ?: device.address,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            // La dirección MAC siempre se muestra como información secundaria.
            // Es útil para identificar dispositivos que puedan tener nombres genéricos o duplicados.
            Text(
                text = device.address,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}
