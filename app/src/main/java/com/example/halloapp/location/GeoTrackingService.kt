package com.example.halloapp.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.halloapp.MainActivity
import com.example.halloapp.data.GeoEvent
import com.example.halloapp.data.GeoPoint
import com.example.halloapp.data.GeoStore
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class GeoTrackingService : Service() {

    private lateinit var store: GeoStore
    private lateinit var locationManager: LocationManager
    private var pointVersion = -1
    private var points: List<GeoPoint> = emptyList()
    private val insidePoints = HashMap<Long, Boolean>()
    private val eventIdCounter = AtomicLong(System.currentTimeMillis())
    private var eventCount = 0

    @Volatile
    private var lastKnownLocation: android.location.Location? = null

    private val locationListener = LocationListener { location ->
        lastKnownLocation = location
        CurrentLocationHolder.lat = location.latitude
        CurrentLocationHolder.lon = location.longitude
        CurrentLocationHolder.lastFixAt = System.currentTimeMillis()
        onLocation(location.latitude, location.longitude)
    }

    override fun onCreate() {
        super.onCreate()
        store = GeoStore(File(filesDir, "geo"))
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        points = store.loadPoints()
        pointVersion = points.hashCode()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        updateProviderState()
        requestLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MARK -> if (hasLocationPermission()) markCurrentPosition(
                intent.getStringExtra(EXTRA_NAME) ?: "Punkt"
            )
            ACTION_SAVE_POINT_AT -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)
                if (!lat.isNaN() && !lon.isNaN()) {
                    savePoint(lat, lon, intent.getStringExtra(EXTRA_NAME) ?: "Punkt")
                }
            }
            ACTION_REFRESH_POINTS -> {
                points = store.loadPoints()
                pointVersion = points.hashCode()
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(locationListener) }
        super.onDestroy()
    }

    private fun updateProviderState() {
        CurrentLocationHolder.gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        CurrentLocationHolder.networkEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationUpdates() {
        if (!hasLocationPermission()) return
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MS,
                LOCATION_MIN_DISTANCE_M,
                locationListener,
                mainLooper
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LOCATION_INTERVAL_MS,
                LOCATION_MIN_DISTANCE_M,
                locationListener,
                mainLooper
            )
        }
    }

    private fun markCurrentPosition(name: String) {
        val fallback = runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        val last = lastKnownLocation ?: fallback
        if (last != null) {
            savePoint(last.latitude, last.longitude, name)
        } else {
            pendingName = name
            requestSingleFix()
        }
    }

    private var pendingName: String? = null
    private var singleFixListener: LocationListener? = null

    private fun requestSingleFix() {
        if (singleFixListener != null) return
        val listener = LocationListener { location ->
            lastKnownLocation = location
            CurrentLocationHolder.lat = location.latitude
            CurrentLocationHolder.lon = location.longitude
            onLocation(location.latitude, location.longitude)
            pendingName?.let { savePoint(location.latitude, location.longitude, it) }
            pendingName = null
            singleFixListener?.let { runCatching { locationManager.removeUpdates(it) } }
            singleFixListener = null
        }
        singleFixListener = listener
        runCatching {
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                listener,
                mainLooper
            )
            locationManager.requestSingleUpdate(
                LocationManager.NETWORK_PROVIDER,
                listener,
                mainLooper
            )
        }
    }

    private fun savePoint(lat: Double, lon: Double, name: String) {
        points = store.loadPoints()
        pointVersion = points.hashCode()
        val newPoint = GeoPoint(
            id = System.currentTimeMillis(),
            name = name,
            lat = lat,
            lon = lon,
            createdAt = System.currentTimeMillis()
        )
        store.savePoints(points + newPoint)
        points = points + newPoint
        updateNotification()
    }

    private fun onLocation(lat: Double, lon: Double) {
        val current = store.loadPoints()
        if (current.hashCode() != pointVersion) {
            points = current
            pointVersion = current.hashCode()
            (insidePoints.keys - current.map { it.id }.toSet()).forEach { insidePoints.remove(it) }
        } else {
            points = current
        }
        current.forEach { p ->
            if (!insidePoints.containsKey(p.id)) insidePoints[p.id] = false
        }
        points.forEach { point ->
            val distance = GeoStore.distanceBetween(lat, lon, point.lat, point.lon)
            val wasInside = insidePoints[point.id] ?: false
            val isInside = distance <= ENTER_RADIUS_M
            if (isInside && !wasInside) {
                insidePoints[point.id] = true
                logEvent(point, "enter", lat, lon, distance)
            } else if (wasInside && distance > EXIT_RADIUS_M) {
                insidePoints[point.id] = false
                logEvent(point, "exit", lat, lon, distance)
            }
        }
    }

    private fun logEvent(point: GeoPoint, type: String, lat: Double, lon: Double, distance: Float) {
        val event = GeoEvent(
            id = eventIdCounter.incrementAndGet(),
            pointId = point.id,
            pointName = point.name,
            type = type,
            lat = lat,
            lon = lon,
            distance = distance,
            timestamp = System.currentTimeMillis()
        )
        store.appendEvent(event)
        eventCount++
        updateNotification()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Standort-Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Geo-Tracking aktiv")
            .setContentText("${store.loadPoints().size} Punkte markiert")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        const val ACTION_MARK = "com.example.halloapp.action.MARK"
        const val ACTION_SAVE_POINT_AT = "com.example.halloapp.action.SAVE_POINT_AT"
        const val EXTRA_NAME = "com.example.halloapp.extra.NAME"
        const val EXTRA_LAT = "com.example.halloapp.extra.LAT"
        const val EXTRA_LON = "com.example.halloapp.extra.LON"
        const val ACTION_REFRESH_POINTS = "com.example.halloapp.action.REFRESH_POINTS"
        const val ACTION_STOP = "com.example.halloapp.action.STOP"
        const val ENTER_RADIUS_M = 25.0f
        const val EXIT_RADIUS_M = 50.0f
        private const val CHANNEL_ID = "geo_tracking"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_INTERVAL_MS = 10_000L
        private const val LOCATION_MIN_DISTANCE_M = 10f

        fun start(context: Context) {
            val intent = Intent(context, GeoTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun mark(context: Context, name: String) {
            val intent = Intent(context, GeoTrackingService::class.java)
                .setAction(ACTION_MARK)
                .putExtra(EXTRA_NAME, name)
            startForegroundSafely(context, intent)
        }

        fun savePointAt(context: Context, lat: Double, lon: Double, name: String) {
            val intent = Intent(context, GeoTrackingService::class.java)
                .setAction(ACTION_SAVE_POINT_AT)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_LAT, lat)
                .putExtra(EXTRA_LON, lon)
            startForegroundSafely(context, intent)
        }

        fun refreshPoints(context: Context) {
            val intent = Intent(context, GeoTrackingService::class.java).setAction(ACTION_REFRESH_POINTS)
            startForegroundSafely(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GeoTrackingService::class.java).setAction(ACTION_STOP)
            startForegroundSafely(context, intent)
        }

        private fun startForegroundSafely(context: Context, intent: Intent) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
