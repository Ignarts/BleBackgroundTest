// FICHERO: DeviceDetailViewModel.kt
// DESCRIPCIÓN: ViewModel para la pantalla `DeviceDetailScreen`.
// Su única responsabilidad es actuar como puente entre la UI y el gestor de estado de la conexión (`ConnectionManager`).

package com.example.backgroundtest

import android.util.Log
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

    /**
     * Expone las características descubiertas del dispositivo BLE.
     */
    val discoveredCharacteristics = ConnectionManager.discoveredCharacteristics.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectionManager.discoveredCharacteristics.value
    )

    /**
     * Expone los datos recibidos de las características.
     */
    val characteristicData = ConnectionManager.characteristicData.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectionManager.characteristicData.value
    )

    /**
     * Expone el par de características descubierto automáticamente (para OBD-II).
     */
    val discoveredPair = ConnectionManager.discoveredPair.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectionManager.discoveredPair.value
    )

    /**
     * Lee el valor de una característica.
     * @param characteristicUuid El UUID de la característica a leer.
     */
    fun readCharacteristic(characteristicUuid: String) {
        val service = BleConnectionService.getInstance()
        if (service == null) {
            Log.e("DeviceDetailVM", "Cannot read characteristic: Service is not running")
            return
        }
        service.readCharacteristic(characteristicUuid)
    }

    /**
     * Escribe datos en una característica.
     * @param characteristicUuid El UUID de la característica.
     * @param data Los datos a escribir como ByteArray.
     */
    fun writeCharacteristic(characteristicUuid: String, data: ByteArray) {
        val service = BleConnectionService.getInstance()
        if (service == null) {
            Log.e("DeviceDetailVM", "Cannot write characteristic: Service is not running")
            return
        }
        service.writeCharacteristic(characteristicUuid, data)
    }

    /**
     * Escribe datos en una característica desde un String.
     * @param characteristicUuid El UUID de la característica.
     * @param text El texto a escribir (se convertirá a ByteArray usando UTF-8).
     */
    fun writeCharacteristicText(characteristicUuid: String, text: String) {
        writeCharacteristic(characteristicUuid, text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Escribe datos en una característica desde un String hexadecimal.
     * @param characteristicUuid El UUID de la característica.
     * @param hexString String hexadecimal (ej: "01A2FF").
     */
    fun writeCharacteristicHex(characteristicUuid: String, hexString: String) {
        try {
            val data = hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            writeCharacteristic(characteristicUuid, data)
        } catch (e: Exception) {
            Log.e("DeviceDetailVM", "Invalid hex string: $hexString", e)
        }
    }

    /**
     * Habilita o deshabilita las notificaciones de una característica.
     * @param characteristicUuid El UUID de la característica.
     * @param enable true para habilitar, false para deshabilitar.
     */
    fun setCharacteristicNotification(characteristicUuid: String, enable: Boolean) {
        val service = BleConnectionService.getInstance()
        if (service == null) {
            Log.e("DeviceDetailVM", "Cannot set notification: Service is not running")
            return
        }
        service.setCharacteristicNotification(characteristicUuid, enable)
    }

    /**
     * Limpia los datos de características almacenados.
     */
    fun clearCharacteristicData() {
        ConnectionManager.clearCharacteristicData()
    }
}
