package com.gpstracking.Services

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.gpstracking.R
import com.gpstracking.Room.AppDatabase
import com.gpstracking.Room.LocationPointEntity
import com.gpstracking.Room.TrackingSessionEntity
import com.gpstracking.utils.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var callback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val notificationId = 1001
    private var sessionId = 0L
    private var isTracking = false
    private var currentLat = 0.0
    private var currentLng = 0.0
    private var pointsCount = 0

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_LOCATION_UPDATE = "com.gpstracking.ACTION_LOCATION_UPDATE"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createChannel(this)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isTracking) {
                    startTracking()
                }
            }
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        startForeground(notificationId, buildNotification())

        serviceScope.launch(Dispatchers.IO) {
            sessionId = AppDatabase.get(this@LocationTrackingService)
                .sessionDao()
                .insertSession(
                    TrackingSessionEntity(
                        startTime = System.currentTimeMillis()
                    )
                )
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).apply {
            setMinUpdateIntervalMillis(1500L)
            setMaxUpdateDelayMillis(4000L)
        }.build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLat = location.latitude
                    currentLng = location.longitude
                    pointsCount++

                    serviceScope.launch(Dispatchers.IO) {
                        AppDatabase.get(this@LocationTrackingService)
                            .locationDao()
                            .insertLocation(
                                LocationPointEntity(
                                    sessionId = sessionId,
                                    lat = location.latitude,
                                    lng = location.longitude,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                    }

                    updateNotification()

                    val intent = Intent(ACTION_LOCATION_UPDATE).apply {
                        putExtra(EXTRA_LAT, location.latitude)
                        putExtra(EXTRA_LNG, location.longitude)
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                   // sendBroadcast(intent)
                    LocalBroadcastManager.getInstance(this@LocationTrackingService)
                        .sendBroadcast(intent)

                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
            isTracking = true
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun stopTracking() {
        if (::callback.isInitialized) {
            fusedClient.removeLocationUpdates(callback)
        }

        serviceScope.launch(Dispatchers.IO) {
            AppDatabase.get(this@LocationTrackingService)
                .sessionDao()
                .endSession(sessionId, System.currentTimeMillis())
        }

        isTracking = false
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val contentText = if (pointsCount > 0) {
            "Lat: ${String.format("%.6f", currentLat)}, Lng: ${String.format("%.6f", currentLng)}"
        } else {
            "Waiting for location..."
        }

        return NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(R.drawable.location_dialog)
            .setContentTitle("GPS Tracking Active")
            .setContentText(contentText)
            .setSubText("Points recorded: $pointsCount")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.tag_cross_svgrepo_com,
                "Stop Tracking",
                stopPending
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(notificationId, buildNotification())
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}