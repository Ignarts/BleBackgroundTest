// FICHERO: DeviceDetailScreen.kt
// DESCRIPCIÓN: Define la interfaz de usuario que muestra los detalles y el estado de conexión de un dispositivo BLE específico.

package com.example.backgroundtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Pantalla que muestra el estado de la conexión con un dispositivo BLE.
 * Se actualiza en tiempo real gracias al `ViewModel` y al `StateFlow`.
 *
 * @param deviceName El nombre del dispositivo al que estamos conectados.
 * @param onDisconnect Lambda que se ejecuta cuando el usuario pulsa el botón de desconectar.
 * @param viewModel El ViewModel que proporciona el estado de la conexión. Se inyecta automáticamente por Hilt o por `viewModel()`.
 */
@Composable
fun DeviceDetailScreen(
    deviceName: String?,
    onDisconnect: () -> Unit,
    viewModel: DeviceDetailViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val characteristics by viewModel.discoveredCharacteristics.collectAsState()
    val characteristicData by viewModel.characteristicData.collectAsState()
    val discoveredPair by viewModel.discoveredPair.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header con estado de conexión
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Dispositivo: ${deviceName ?: "Desconocido"}",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (connectionState) {
                    ConnectionState.CONNECTING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("Conectando...", fontSize = 16.sp)
                        }
                    }
                    ConnectionState.CONNECTED -> {
                        Text(
                            "✓ Conectado",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    ConnectionState.DISCONNECTED -> {
                        Text("⟳ Desconectado (Reconectando...)", fontSize = 16.sp)
                    }
                    ConnectionState.DISCONNECTING -> {
                        Text("Desconectando...", fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Desconectar y Volver")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mostrar par de características auto-descubierto
        discoveredPair?.let { pair ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "✓ Par OBD-II Auto-descubierto:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Read (Notify): ${pair.read.take(8)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        "Write: ${pair.write.take(8)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        "Service: ${pair.service.take(8)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Mostrar datos recibidos
        characteristicData?.let { data ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Último dato recibido:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "UUID: ${data.characteristicUuid.takeLast(12)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        "Hex: ${data.data.joinToString(" ") { "%02X".format(it) }}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        "ASCII: ${data.data.toString(Charsets.UTF_8)}",
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Lista de características
        Text(
            text = "Características (${characteristics.size}):",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (characteristics.isEmpty()) {
            if (connectionState == ConnectionState.CONNECTED) {
                Text(
                    "Descubriendo servicios...",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "No hay características disponibles",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(characteristics) { characteristic ->
                    CharacteristicCard(
                        characteristic = characteristic,
                        onRead = { viewModel.readCharacteristic(it) },
                        onWrite = { uuid, data -> viewModel.writeCharacteristic(uuid, data) },
                        onNotify = { uuid, enable -> viewModel.setCharacteristicNotification(uuid, enable) }
                    )
                }
            }
        }
    }
}

@Composable
fun CharacteristicCard(
    characteristic: BleCharacteristic,
    onRead: (String) -> Unit,
    onWrite: (String, ByteArray) -> Unit,
    onNotify: (String, Boolean) -> Unit
) {
    var writeText by remember { mutableStateOf("") }
    var notifyEnabled by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // UUID - Mostrar la parte importante (primeros 8 caracteres que identifican la característica)
            val shortUuid = if (characteristic.uuid.startsWith("0000") && characteristic.uuid.contains("-0000-1000-8000")) {
                // Es un UUID estándar Bluetooth, mostrar solo la parte significativa
                characteristic.uuid.substring(0, 8)
            } else {
                // Es un UUID personalizado, mostrar los primeros 20 caracteres
                characteristic.uuid.take(20)
            }

            Text(
                text = shortUuid,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleSmall
            )

            // Mostrar UUID completo en texto más pequeño
            Text(
                text = characteristic.uuid,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Propiedades
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (characteristic.canRead) {
                    Text("R", fontSize = 10.sp, modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(4.dp))
                }
                if (characteristic.canWrite) {
                    Text("W", fontSize = 10.sp, modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(4.dp))
                }
                if (characteristic.canNotify) {
                    Text("N", fontSize = 10.sp, modifier = Modifier
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(4.dp))
                }
            }

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (characteristic.canRead) {
                    OutlinedButton(
                        onClick = { onRead(characteristic.uuid) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Leer", fontSize = 12.sp)
                    }
                }

                if (characteristic.canWrite) {
                    OutlinedButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (expanded) "▼" else "Escribir", fontSize = 12.sp)
                    }
                }

                if (characteristic.canNotify) {
                    OutlinedButton(
                        onClick = {
                            notifyEnabled = !notifyEnabled
                            onNotify(characteristic.uuid, notifyEnabled)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (notifyEnabled) "✓ Notify" else "Notify", fontSize = 12.sp)
                    }
                }
            }

            // Sección de escritura expandible
            if (expanded && characteristic.canWrite) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = writeText,
                    onValueChange = { writeText = it },
                    label = { Text("Texto o Hex (01A2FF)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onWrite(characteristic.uuid, writeText.toByteArray(Charsets.UTF_8))
                            writeText = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enviar Texto", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            try {
                                val data = writeText.replace(" ", "").chunked(2)
                                    .map { it.toInt(16).toByte() }.toByteArray()
                                onWrite(characteristic.uuid, data)
                                writeText = ""
                            } catch (e: Exception) {
                                // Error en formato hex
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enviar Hex", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
