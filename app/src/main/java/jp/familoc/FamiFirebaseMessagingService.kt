package jp.familoc

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FamiFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val requestId = message.data["request_id"]?.takeIf(::isValidRequestId) ?: return
        try {
            if (!Backend.verifyRequest(requestId)) return
            if (message.priority != RemoteMessage.PRIORITY_HIGH) {
                report(requestId, "unavailable", "fcm_priority_downgraded")
                return
            }
            val preferences = getSharedPreferences("requests", MODE_PRIVATE)
            synchronized(lock) {
                if (preferences.contains(requestId)) return
                preferences.edit().putLong(requestId, System.currentTimeMillis()).apply()
            }
            val intent = Intent(this, LocationForegroundService::class.java)
                .putExtra(LocationForegroundService.REQUEST_ID, requestId)
            try {
                startForegroundService(intent)
            } catch (error: Exception) {
                if (Build.VERSION.SDK_INT < 31 || error !is ForegroundServiceStartNotAllowedException) {
                    if (error !is SecurityException) throw error
                }
                report(requestId, "unavailable", error.javaClass.simpleName)
            }
        } catch (error: Exception) {
            report(requestId, "error", error.javaClass.simpleName)
        }
    }

    override fun onNewToken(token: String) {
        runCatching { Backend.uploadFcmToken(token) }
    }

    private fun report(requestId: String, status: String, error: String) =
        runCatching { Backend.postStatus(requestId, status, error) }

    companion object {
        private val lock = Any()
    }
}

private val REQUEST_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
internal fun isValidRequestId(value: String) = REQUEST_ID.matches(value)
