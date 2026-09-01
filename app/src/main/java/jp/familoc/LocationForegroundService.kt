package jp.familoc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocationForegroundService : Service() {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val finished = AtomicBoolean()
    private var best: Location? = null
    private var requestId: String? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                if (best == null || location.accuracy < best!!.accuracy) best = location
                if (location.hasAccuracy() && location.accuracy <= DESIRED_ACCURACY_METERS) finish(location)
            }
        }
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    @SuppressLint("MissingPermission") // requirePermissionsAndLocation() runs before the location API call.
    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(REQUEST_ID) ?: return stopNow()
        if (requestId != null) {
            if (requestId != id) report(id, "unavailable", "another_request_is_active")
            return START_NOT_STICKY
        }
        requestId = id
        try {
            createNotificationChannel()
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("現在地を家族に共有しています")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            requirePermissionsAndLocation()
            report(id, "locating")
            client.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000).setMinUpdateIntervalMillis(500).build(),
                callback,
                Looper.getMainLooper(),
            ).addOnFailureListener { failAndStop(id, "unavailable", it.message ?: it.javaClass.simpleName) }
            handler.postDelayed({ finish(best) }, TIMEOUT_MILLIS)
        } catch (error: Exception) {
            failAndStop(id, "unavailable", error.message ?: error.javaClass.simpleName)
        }
        return START_NOT_STICKY
    }

    private fun requirePermissionsAndLocation() {
        check(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            "fine_location_missing"
        }
        if (Build.VERSION.SDK_INT >= 29) {
            check(checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                "background_location_missing"
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            check(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                "notification_permission_missing"
            }
        }
        val manager = getSystemService(LocationManager::class.java)
        check(manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            "location_services_disabled"
        }
    }

    private fun finish(location: Location?) {
        if (!finished.compareAndSet(false, true)) return
        handler.removeCallbacksAndMessages(null)
        client.removeLocationUpdates(callback)
        val id = requestId ?: run {
            stopNow()
            return
        }
        executor.execute {
            try {
                if (location == null) Backend.postStatus(id, "timeout", "no_location")
                else Backend.uploadLocation(id, location)
            } catch (error: Exception) {
                runCatching { Backend.postStatus(id, "error", error.message ?: error.javaClass.simpleName) }
            } finally {
                stopNow()
            }
        }
    }

    private fun report(id: String, status: String, error: String? = null) =
        executor.execute { runCatching { Backend.postStatus(id, status, error) } }

    private fun failAndStop(id: String, status: String, error: String) {
        if (!finished.compareAndSet(false, true)) return
        handler.removeCallbacksAndMessages(null)
        client.removeLocationUpdates(callback)
        executor.execute {
            runCatching { Backend.postStatus(id, status, error) }
            stopNow()
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "位置情報の共有", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stopNow(): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        client.removeLocationUpdates(callback)
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val REQUEST_ID = "request_id"
        private const val CHANNEL_ID = "location-sharing"
        private const val NOTIFICATION_ID = 1001
        private const val DESIRED_ACCURACY_METERS = 25f
        private const val TIMEOUT_MILLIS = 15_000L
    }
}
