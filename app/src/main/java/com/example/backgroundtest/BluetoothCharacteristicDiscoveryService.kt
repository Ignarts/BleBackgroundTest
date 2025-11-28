// FICHERO: BluetoothCharacteristicDiscoveryService.kt
// DESCRIPCIÓN: Servicio responsable de descubrir el mejor par de características BLE
// para dongles OBD-II. Busca características notificables + escritura sin respuesta.

package com.example.backgroundtest

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log

/**
 * Representa el par de características descubiertas para comunicación BLE.
 */
data class BluetoothCharacteristicPair(
    val read: String,      // UUID de la característica de lectura (notifiable)
    val write: String,     // UUID de la característica de escritura (writeWithoutResponse)
    val service: String    // UUID del servicio que contiene estas características
)

/**
 * Excepción lanzada cuando no se encuentra un par de características adecuado.
 */
class BluetoothCharacteristicsNotFoundError : Exception(
    "No se encontró un par de características adecuado (notifiable + writeWithoutResponse)"
)

/**
 * Servicio responsable de descubrir el mejor par de características BLE
 * para dongles OBD-II. Busca características notificables + escritura sin respuesta.
 */
class BluetoothCharacteristicDiscoveryService {

    companion object {
        private const val TAG = "CharacteristicDiscovery"
    }

    /**
     * Descubre el mejor par de características para un dispositivo BLE dado.
     * @param gatt - El objeto BluetoothGatt con los servicios descubiertos.
     * @return BluetoothCharacteristicPair con los UUIDs de lectura, escritura y servicio.
     * @throws BluetoothCharacteristicsNotFoundError si no se encuentra un par adecuado.
     */
    fun discover(gatt: BluetoothGatt): BluetoothCharacteristicPair {
        val services = gatt.services ?: emptyList()

        Log.i(TAG, "Starting characteristic discovery for device ${gatt.device.address}")
        Log.i(TAG, "Found ${services.size} services")

        for (service in services) {
            val characteristics = service.characteristics ?: continue
            Log.d(TAG, "Analyzing service ${service.uuid} with ${characteristics.size} characteristics")

            // Buscar característica notificable (para lectura/recepción de datos)
            val notifiableChar = characteristics.find { char ->
                char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            }

            // Buscar característica con escritura sin respuesta (para envío de comandos)
            val writeWithoutResponseChar = characteristics.find { char ->
                char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            }

            // Si encontramos ambas características en el mismo servicio, retornar el par
            if (notifiableChar != null && writeWithoutResponseChar != null) {
                Log.i(
                    TAG,
                    "✓ Found suitable pair in service ${service.uuid}:\n" +
                    "  - Notifiable (read): ${notifiableChar.uuid}\n" +
                    "  - WriteWithoutResponse (write): ${writeWithoutResponseChar.uuid}"
                )

                return BluetoothCharacteristicPair(
                    read = notifiableChar.uuid.toString(),
                    write = writeWithoutResponseChar.uuid.toString(),
                    service = service.uuid.toString()
                )
            }

            // Log de depuración si solo se encuentra una
            if (notifiableChar != null) {
                Log.d(TAG, "  Found notifiable: ${notifiableChar.uuid} but no writeWithoutResponse")
            }
            if (writeWithoutResponseChar != null) {
                Log.d(TAG, "  Found writeWithoutResponse: ${writeWithoutResponseChar.uuid} but no notifiable")
            }
        }

        // Si no encontramos un par adecuado, lanzar excepción
        Log.w(TAG, "✗ No suitable characteristic pair found (notifiable + writeWithoutResponse)")
        throw BluetoothCharacteristicsNotFoundError()
    }

    /**
     * Versión alternativa que busca cualquier par válido, no necesariamente en el mismo servicio.
     * Útil si el dongle tiene las características en servicios separados.
     */
    fun discoverAcrossServices(gatt: BluetoothGatt): BluetoothCharacteristicPair {
        val services = gatt.services ?: emptyList()

        Log.i(TAG, "Starting cross-service characteristic discovery for device ${gatt.device.address}")

        var notifiableChar: BluetoothGattCharacteristic? = null
        var notifiableService: String? = null
        var writeWithoutResponseChar: BluetoothGattCharacteristic? = null
        var writeService: String? = null

        // Buscar en todos los servicios
        for (service in services) {
            val characteristics = service.characteristics ?: continue

            for (char in characteristics) {
                if (notifiableChar == null &&
                    char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    notifiableChar = char
                    notifiableService = service.uuid.toString()
                    Log.d(TAG, "Found notifiable: ${char.uuid} in service ${service.uuid}")
                }

                if (writeWithoutResponseChar == null &&
                    char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                    writeWithoutResponseChar = char
                    writeService = service.uuid.toString()
                    Log.d(TAG, "Found writeWithoutResponse: ${char.uuid} in service ${service.uuid}")
                }

                // Si ya encontramos ambas, podemos retornar
                if (notifiableChar != null && writeWithoutResponseChar != null) {
                    Log.i(
                        TAG,
                        "✓ Found characteristics across services:\n" +
                        "  - Notifiable (read): ${notifiableChar.uuid} in ${notifiableService}\n" +
                        "  - WriteWithoutResponse (write): ${writeWithoutResponseChar.uuid} in ${writeService}"
                    )

                    // Preferir usar el servicio del notifiable como servicio principal
                    return BluetoothCharacteristicPair(
                        read = notifiableChar.uuid.toString(),
                        write = writeWithoutResponseChar.uuid.toString(),
                        service = notifiableService!!
                    )
                }
            }
        }

        Log.w(TAG, "✗ Could not find both characteristics across all services")
        throw BluetoothCharacteristicsNotFoundError()
    }
}
