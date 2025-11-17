// FICHERO: DeviceDetailScreen.kt
// DESCRIPCIÓN: Define la interfaz de usuario que muestra los detalles y el estado de conexión de un dispositivo BLE específico.

package com.example.backgroundtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // Se suscribe al StateFlow del ViewModel. Cada vez que el estado cambia, esta variable se actualiza
    // y la UI se recompone automáticamente para reflejar el nuevo estado.
    val connectionState by viewModel.connectionState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Dispositivo: ${deviceName ?: "Desconocido"}", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // El bloque `when` reacciona al estado de conexión actual para mostrar la UI correspondiente.
        when (connectionState) {
            ConnectionState.CONNECTING -> {
                // Muestra un indicador de progreso mientras se establece la conexión.
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Estado: Conectando...", fontSize = 20.sp)
            }
            ConnectionState.CONNECTED -> {
                Text("Estado: Conectado", fontSize = 20.sp)
            }
            ConnectionState.DISCONNECTED -> {
                // Este estado también gestiona la reconexión automática.
                Text("Estado: Desconectado. Intentando reconectar...", fontSize = 20.sp)
            }
            ConnectionState.DISCONNECTING -> {
                Text("Estado: Desconectando...", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDisconnect) {
            Text("Desconectar y Volver")
        }
    }
}
