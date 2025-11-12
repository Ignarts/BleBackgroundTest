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

@Composable
fun DeviceDetailScreen(
    deviceName: String?,
    onDisconnect: () -> Unit,
    viewModel: DeviceDetailViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Dispositivo: ${deviceName ?: "Desconocido"}", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        when (connectionState) {
            ConnectionState.CONNECTING -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Estado: Conectando...", fontSize = 20.sp)
            }
            ConnectionState.CONNECTED -> {
                Text("Estado: Conectado", fontSize = 20.sp)
            }
            ConnectionState.DISCONNECTED -> {
                Text("Estado: Desconectado", fontSize = 20.sp)
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
