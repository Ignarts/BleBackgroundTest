// En tu fichero DeviceListScreen.kt

package com.example.backgroundtest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
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

@SuppressLint("MissingPermission")
@Composable
fun DeviceListScreen(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onScanClick: () -> Unit
) {
    // Usamos un Column como contenedor principal que ocupa toda la pantalla
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 25.dp, vertical = 40.dp)
    ) {
        // --- 1. SECCIÓN DEL BOTÓN ---
        // Esta sección está en la parte superior.
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

        // Espacio grande para separar visualmente el botón de la lista
        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. SECCIÓN DE LA LISTA ---
        // Ocupa el espacio restante, pero con un máximo del 50% del alto total.
        Text(
            "Dispositivos Encontrados",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
        ) {
            LazyColumn {
                items(devices) { device ->
                    DeviceItem(device = device, onDeviceClick = onDeviceClick)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onDeviceClick: (BluetoothDevice) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onDeviceClick(device) },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Si el nombre es nulo, mostramos la dirección MAC en su lugar.
            Text(
                text = device.name ?: device.address, // CAMBIO AQUÍ
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            // La dirección siempre estará presente y es clave para identificar el dongle.
            Text(
                text = device.address,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}
