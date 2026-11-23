package com.arnit.imagex.backend

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PremiumStatus(
    val premium: Boolean,
    val plan: String?,
    val expiresAt: String?,
    val expired: Boolean
)

data class PaymentOrder(
    val orderId: String,
    val paymentSessionId: String,
    val amount: Double,
    val currency: String,
    val environment: String
)

data class PaymentVerification(
    val status: String,
    val premiumUnlocked: Boolean,
    val plan: String?,
    val expiresAt: String?
)

class ImageXBackendClient(
    private val baseUrl: String = "https://abruar.online/imagex/cashfree/"
) {
    suspend fun premiumStatus(): PremiumStatus = withContext(Dispatchers.IO) {
        val json = authenticatedRequest("status.php", "GET")
        PremiumStatus(
            premium = json.optBoolean("premium", false),
            plan = json.optString("plan").takeIf { it.isNotBlank() },
            expiresAt = json.optString("expires_at").takeIf { it.isNotBlank() },
            expired = json.optBoolean("expired", false)
        )
    }

    suspend fun createPaymentOrder(plan: String, phone: String): PaymentOrder = withContext(Dispatchers.IO) {
        require(plan in setOf("monthly", "yearly", "lifetime")) { "Invalid payment plan." }
        val json = authenticatedRequest(
            "create-order.php",
            "POST",
            JSONObject().put("plan", plan).put("phone", phone)
        )
        PaymentOrder(
            orderId = json.optString("order_id").takeIf { it.isNotBlank() }
                ?: error("Backend did not return an order ID."),
            paymentSessionId = json.optString("payment_session_id").takeIf { it.isNotBlank() }
                ?: error("Backend did not return a payment session."),
            amount = json.optDouble("amount"),
            currency = json.optString("currency", "INR"),
            environment = json.optString("environment", "sandbox")
        )
    }

    suspend fun verifyPayment(orderId: String): PaymentVerification = withContext(Dispatchers.IO) {
        val json = authenticatedRequest(
            "verify.php",
            "POST",
            JSONObject().put("order_id", orderId)
        )
        PaymentVerification(
            status = json.optString("status", "UNKNOWN"),
            premiumUnlocked = json.optBoolean("premium_unlocked", false),
            plan = json.optString("plan").takeIf { it.isNotBlank() },
            expiresAt = json.optString("expires_at").takeIf { it.isNotBlank() }
        )
    }

    private suspend fun authenticatedRequest(
        path: String,
        method: String,
        body: JSONObject? = null
    ): JSONObject {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Please sign in first.")
        val token = user.getIdToken(false).await().token ?: error("Firebase token unavailable.")
        val connection = (URL(baseUrl.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Backend request failed (HTTP $code): $text")
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
