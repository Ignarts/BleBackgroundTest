package com.example.backgroundtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeviceDetailScreen(
    deviceName: String?,
    onDisconnect: () -> Unit
) {
    // FIX: Collect the StateFlow as a State to properly observe it in Compose.
    val connectionState by ConnectionManager.connectionState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = deviceName ?: "Unknown Device", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        // The text will now automatically update whenever the StateFlow emits a new value.
        Text(text = "Status: $connectionState", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDisconnect) {
            Text(text = "Disconnect")
        }
    }
}
