package com.example.backgroundtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class DeviceDetailViewModel : ViewModel() {
    // Exponemos el estado del ConnectionManager para que la UI lo observe.
    val connectionState = ConnectionManager.connectionState.stateIn(
        scope = viewModelScope, // El estado sobrevive mientras el ViewModel esté vivo
        started = SharingStarted.WhileSubscribed(5000), // Se mantiene activo 5s después de que la UI deje de observar
        initialValue = ConnectionManager.connectionState.value
    )
}
