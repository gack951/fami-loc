package jp.familoc

import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

object Backend {
    private fun connection(path: String, method: String): HttpURLConnection {
        check(BuildConfig.API_BASE_URL.startsWith("https://")) { "HTTPS API endpoint is not configured" }
        check(BuildConfig.DEVICE_TOKEN.isNotBlank()) { "Device token is not configured" }
        return URI(BuildConfig.API_BASE_URL.trimEnd('/') + path).toURL().openConnection().let {
            it as HttpURLConnection
        }.apply {
            requestMethod = method
            connectTimeout = 4_000
            readTimeout = 4_000
            setRequestProperty("Authorization", "Bearer ${BuildConfig.DEVICE_TOKEN}")
            setRequestProperty("Content-Type", "application/json")
        }
    }

    fun verifyRequest(requestId: String): Boolean =
        connection("/api/location-requests/$requestId", "GET").use { it.responseCode == 200 }

    fun uploadLocation(requestId: String, location: Location) {
        val body = JSONObject()
            .put("request_id", requestId)
            .put("latitude", location.latitude)
            .put("longitude", location.longitude)
            .put("accuracy", location.accuracy.toDouble())
            .put("captured_at", location.time)
            .toString()
        connection("/api/location-requests/$requestId/location", "POST").send(body)
    }

    fun postStatus(requestId: String, status: String, error: String? = null) {
        val body = JSONObject().put("status", status).apply { error?.let { put("error", it) } }.toString()
        connection("/api/location-requests/$requestId/status", "POST").send(body)
    }

    fun uploadFcmToken(token: String) {
        connection("/api/devices/fcm-token", "POST").send(JSONObject().put("fcm_token", token).toString())
    }

    private fun HttpURLConnection.send(body: String) {
        doOutput = true
        outputStream.use { it.write(body.toByteArray()) }
        check(responseCode in 200..299) { "Backend returned HTTP $responseCode" }
        disconnect()
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try { block(this) } finally { disconnect() }
}
