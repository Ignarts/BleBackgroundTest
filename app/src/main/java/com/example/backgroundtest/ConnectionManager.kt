// FICHERO: ConnectionManager.kt
// DESCRIPCIÓN: Objeto singleton que actúa como la "única fuente de verdad" para el estado de la conexión BLE.

package com.example.backgroundtest

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
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
 * Representa una característica BLE descubierta con sus propiedades.
 */
data class BleCharacteristic(
    val uuid: String,
    val serviceUuid: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canNotify: Boolean,
    val canIndicate: Boolean
)

/**
 * Representa los datos recibidos de una característica BLE.
 */
data class CharacteristicData(
    val characteristicUuid: String,
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CharacteristicData

        if (characteristicUuid != other.characteristicUuid) return false
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = characteristicUuid.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
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

    // StateFlow para las características descubiertas
    private val _discoveredCharacteristics = MutableStateFlow<List<BleCharacteristic>>(emptyList())
    val discoveredCharacteristics = _discoveredCharacteristics.asStateFlow()

    // StateFlow para los datos recibidos de las características
    private val _characteristicData = MutableStateFlow<CharacteristicData?>(null)
    val characteristicData = _characteristicData.asStateFlow()

    // StateFlow para el par de características descubierto (para OBD-II)
    private val _discoveredPair = MutableStateFlow<BluetoothCharacteristicPair?>(null)
    val discoveredPair = _discoveredPair.asStateFlow()

    /**
     * Método para actualizar el estado de la conexión.
     * Es llamado desde el `BleConnectionService` para notificar a toda la app
     * sobre cambios en la conexión (ej. conectado, desconectado, etc.).
     * @param newState El nuevo estado de la conexión.
     */
    fun updateState(newState: ConnectionState) {
        _connectionState.value = newState
        // Limpiar características cuando se desconecta
        if (newState == ConnectionState.DISCONNECTED || newState == ConnectionState.DISCONNECTING) {
            _discoveredCharacteristics.value = emptyList()
            _discoveredPair.value = null
        }
    }

    /**
     * Actualiza la lista de características descubiertas.
     * Solo actualiza si la lista ha cambiado para evitar recomposiciones innecesarias.
     */
    fun updateDiscoveredCharacteristics(characteristics: List<BleCharacteristic>) {
        // Solo actualizar si la lista es diferente
        if (_discoveredCharacteristics.value != characteristics) {
            _discoveredCharacteristics.value = characteristics
        }
    }

    /**
     * Actualiza los datos recibidos de una característica.
     */
    fun updateCharacteristicData(data: CharacteristicData) {
        _characteristicData.value = data
    }

    /**
     * Limpia los datos de características.
     */
    fun clearCharacteristicData() {
        _characteristicData.value = null
    }

    /**
     * Actualiza el par de características descubierto.
     */
    fun updateDiscoveredPair(pair: BluetoothCharacteristicPair?) {
        _discoveredPair.value = pair
    }
}
