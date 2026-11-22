package com.arnit.imagex.backend

import com.arnit.imagex.auth.FirebaseAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Authenticated ImageX backend client. Image data is never sent by this client.
 * Cashfree checkout is not launched here; this only creates a payment order.
 */
class ImageXBackendClient(
    private val auth: FirebaseAuthRepository = FirebaseAuthRepository(),
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    suspend fun premiumStatus(): PremiumStatus = withContext(Dispatchers.IO) {
        val response = request("GET", "status.php")
        PremiumStatus(
            premium = response.optBoolean("premium", false),
            plan = response.optString("plan").takeIf { it.isNotBlank() },
            expiresAt = response.optString("expires_at").takeIf { it.isNotBlank() },
            expired = response.optBoolean("expired", false)
        )
    }

    suspend fun createPaymentOrder(plan: String, phone: String? = null): PaymentOrder =
        withContext(Dispatchers.IO) {
            require(plan in setOf("monthly", "yearly", "lifetime")) { "Unsupported plan." }
            val body = JSONObject().put("plan", plan)
            phone?.trim()?.takeIf { it.isNotEmpty() }?.let { body.put("phone", it) }
            val response = request("POST", "create-order.php", body)
            PaymentOrder(
                orderId = response.optString("order_id"),
                paymentSessionId = response.optString("payment_session_id"),
                amount = response.optDouble("amount"),
                currency = response.optString("currency", "INR"),
                environment = response.optString("environment", "sandbox")
            )
        }

    private suspend fun request(method: String, path: String, payload: JSONObject? = null): JSONObject {
        val token = auth.getIdToken()
        val connection = (URL(baseUrl.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                throw BackendException(status, json.optString("error").ifBlank { "backend_request_failed" })
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://abruar.online/imagex/cashfree/"
    }
}

data class PremiumStatus(
    val premium: Boolean,
    val plan: String? = null,
    val expiresAt: String? = null,
    val expired: Boolean = false
)

data class PaymentOrder(
    val orderId: String,
    val paymentSessionId: String,
    val amount: Double,
    val currency: String,
    val environment: String
)

class BackendException(val httpStatus: Int, val backendCode: String) :
    Exception("Backend request failed ($httpStatus): $backendCode")
