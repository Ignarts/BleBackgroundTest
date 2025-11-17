// FICHERO: DeviceDetailViewModel.kt
// DESCRIPCIÓN: ViewModel para la pantalla `DeviceDetailScreen`.
// Su única responsabilidad es actuar como puente entre la UI y el gestor de estado de la conexión (`ConnectionManager`).

package com.example.backgroundtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class DeviceDetailViewModel : ViewModel() {

    /**
     * Expone el `StateFlow` del `ConnectionManager` para que la UI de Compose pueda observarlo.
     * Utiliza `stateIn` para convertir un Flow en un StateFlow que es consciente del ciclo de vida del ViewModel.
     */
    val connectionState = ConnectionManager.connectionState.stateIn(
        // El `viewModelScope` asegura que la corrutina se cancele automáticamente cuando el ViewModel se destruya.
        scope = viewModelScope,
        // `SharingStarted.WhileSubscribed(5000)`: El flujo se mantiene activo mientras haya al menos un
        // observador (la UI). Si la UI se va (ej. el usuario rota la pantalla o sale de la app),
        // el flujo se mantiene activo durante 5 segundos más antes de detenerse. Si la UI vuelve
        // en ese tiempo, se reconecta sin perder el estado.
        started = SharingStarted.WhileSubscribed(5000),
        // El valor inicial del flujo es el valor actual que tenga el ConnectionManager.
        initialValue = ConnectionManager.connectionState.value
    )
}
