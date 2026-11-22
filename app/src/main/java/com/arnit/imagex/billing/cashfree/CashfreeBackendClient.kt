package com.arnit.imagex.billing.cashfree

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase-authenticated client for the Cashfree backend starter.
 * This client only creates/verifies orders and reads entitlements. It does not
 * launch Cashfree checkout or unlock Premium on its own.
 */
class CashfreeBackendClient(
    private val baseUrl: String,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    data class CreatedOrder(
        val orderId: String,
        val paymentSessionId: String,
        val amount: Double,
        val currency: String,
        val environment: String
    )

    data class Verification(
        val status: String,
        val premiumUnlocked: Boolean,
        val plan: String?,
        val expiresAt: String?
    )

    data class PremiumStatus(
        val premium: Boolean,
        val plan: String?,
        val expiresAt: String?
    )


    data class CashfreePlan(
        val id: String,
        val name: String,
        val amount: Double,
        val currency: String,
        val description: String
    ) {
        val priceText: String get() = if (currency == "INR") "₹${amount.toInt()}" else "$currency ${amount}"
    }

    suspend fun createOrder(plan: String, phone: String? = null): CreatedOrder =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("plan", plan).apply {
                if (!phone.isNullOrBlank()) put("phone", phone)
            }
            val json = request("create-order.php", "POST", body)
            CreatedOrder(
                orderId = json.getString("order_id"),
                paymentSessionId = json.getString("payment_session_id"),
                amount = json.getDouble("amount"),
                currency = json.getString("currency"),
                environment = json.optString("environment", "sandbox")
            )
        }

    suspend fun verifyOrder(orderId: String): Verification = withContext(Dispatchers.IO) {
        val json = request("verify.php", "POST", JSONObject().put("order_id", orderId))
        Verification(
            status = json.optString("status", "UNKNOWN"),
            premiumUnlocked = json.optBoolean("premium_unlocked", false),
            plan = json.optString("plan").takeIf { it.isNotBlank() },
            expiresAt = json.optString("expires_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun plans(): List<CashfreePlan> = withContext(Dispatchers.IO) {
        val json = request("plans.php", "GET", null)
        val array = json.optJSONArray("plans") ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            CashfreePlan(
                id = item.optString("id"),
                name = item.optString("name"),
                amount = item.optDouble("amount", 0.0),
                currency = item.optString("currency", "INR"),
                description = item.optString("description")
            ).takeIf { it.id.isNotBlank() && it.amount > 0.0 }
        }
    }


    suspend fun premiumStatus(): PremiumStatus = withContext(Dispatchers.IO) {
        val json = request("status.php", "GET", null)
        PremiumStatus(
            premium = json.optBoolean("premium", false),
            plan = json.optString("plan").takeIf { it.isNotBlank() },
            expiresAt = json.optString("expires_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private suspend fun request(path: String, method: String, body: JSONObject?): JSONObject {
        val user = auth.currentUser ?: throw IllegalStateException("Please sign in before purchasing Premium.")
        val tokenResult = user.getIdToken(false).await()
        val token = tokenResult.token ?: throw IllegalStateException("Firebase returned an empty ID token.")
        if (baseUrl.isBlank()) throw IllegalStateException("Cashfree backend URL is not configured for this build.")
        val normalizedBase = baseUrl.trimEnd('/')
        val connection = (URL("$normalizedBase/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) {
                throw IllegalStateException(json.optString("error").ifBlank { "Payment backend request failed (HTTP $code)." })
            }
            json
        } finally {
            connection.disconnect()
        }
    }
}

