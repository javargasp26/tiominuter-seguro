package com.tiominuter.payments

/**
 * La llave NO vive en el código: se inyecta en tiempo de ejecución
 * desde una fuente segura (backend / almacenamiento cifrado).
 */
class PaymentsClient(private val apiKey: String) {
    private val baseUrl = "https://api.example.com/v1"

    fun authHeader(): Pair<String, String> = "Authorization" to "Bearer $apiKey"
}
