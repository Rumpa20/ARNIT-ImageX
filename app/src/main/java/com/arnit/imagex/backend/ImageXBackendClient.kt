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

class ImageXBackendClient(
    private val baseUrl: String = "https://abruar.online/imagex/cashfree/"
) {
    suspend fun premiumStatus(): PremiumStatus = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Please sign in first.")
        val token = user.getIdToken(false).await().token ?: error("Firebase token unavailable.")
        val connection = (URL(baseUrl.trimEnd('/') + "/status.php").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Backend request failed (HTTP $code): $text")
            val json = JSONObject(text)
            PremiumStatus(
                premium = json.optBoolean("premium", false),
                plan = json.optString("plan").takeIf { it.isNotBlank() },
                expiresAt = json.optString("expires_at").takeIf { it.isNotBlank() },
                expired = json.optBoolean("expired", false)
            )
        } finally {
            connection.disconnect()
        }
    }
}
