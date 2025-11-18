package com.example.backgroundtest

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

object ConnectionManager {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private var lastConnectedDeviceAddress: String? = null

    fun updateState(newState: ConnectionState, deviceAddress: String? = null) {
        _connectionState.value = newState
        if (newState == ConnectionState.CONNECTED && deviceAddress != null) {
            lastConnectedDeviceAddress = deviceAddress
            // When connected, stop any background scan that might be running
            // It's important to pass a context to stop the scan.
            // Since ConnectionManager is a singleton, we need a context provider.
            // For now, we assume this is handled where the state is updated from.
        }
    }

    fun onConnectionLost(context: Context) {
        updateState(ConnectionState.DISCONNECTED)
        lastConnectedDeviceAddress?.let { address ->
            BleScanManager.startBackgroundScan(context, address)
        }
    }

    fun stopReconnectionScan(context: Context) {
        lastConnectedDeviceAddress?.let {
            BleScanManager.stopBackgroundScan(context)
        }
    }
}
