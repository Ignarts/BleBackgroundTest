// FICHERO: ConnectionManager.kt
// DESCRIPCIÓN: Objeto singleton que actúa como la "única fuente de verdad" para el estado de la conexión BLE.

package com.example.backgroundtest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Define los posibles estados de una conexión BLE.
 * Esto permite a la UI reaccionar de forma declarativa a los cambios.
 */
enum class ConnectionState {
    DISCONNECTED, // Desconectado, también puede indicar que se está intentando reconectar.
    CONNECTING,   // Conectando activamente por primera vez.
    CONNECTED,    // Conexión establecida y activa.
    DISCONNECTING // Se está procesando una desconexión manual.
}

/**
 * Singleton para gestionar el estado de la conexión de forma centralizada.
 * Al ser un `object`, solo existe una instancia de ConnectionManager en toda la aplicación.
 * Esto permite que componentes dispares (como un Service en segundo plano y una Activity en primer plano)
 * compartan y reaccionen a la misma información de estado de forma segura.
 */
object ConnectionManager {
    // `_connectionState` es un StateFlow mutable y privado. Solo el propio ConnectionManager
    // puede cambiar su valor. Comienza en estado DISCONNECTED.
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    // `connectionState` es la versión pública e inmutable del StateFlow. Otros componentes
    // pueden suscribirse a él para recibir actualizaciones, pero no pueden modificar su valor.
    val connectionState = _connectionState.asStateFlow()

    /**
     * Método para actualizar el estado de la conexión.
     * Es llamado desde el `BleConnectionService` para notificar a toda la app
     * sobre cambios en la conexión (ej. conectado, desconectado, etc.).
     * @param newState El nuevo estado de la conexión.
     */
    fun updateState(newState: ConnectionState) {
        _connectionState.value = newState
    }
}
