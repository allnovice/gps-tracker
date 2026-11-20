package com.personal.gpstracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.app.PendingIntent
import android.content.BroadcastReceiver

class GpsService : Service() {

    companion object {
        private const val TAG = "GpsService"
        private const val CHANNEL_ID = "gps_tracking"
        private const val NOTIFICATION_ID = 1
        private const val API_URL =
            "https://gp.dolly-enigmatic.ts.net/locations"
        private const val DEVICE_ID = "Phone-t5rstp"

        const val PREFS_NAME = "gps_tracker"
        const val KEY_TRACKING = "is_tracking"

        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ACCURACY = "accuracy"
        const val KEY_LAST_UPLOAD = "last_upload"
        const val KEY_SERVER_CONNECTED = "server_connected"

        const val KEY_UPLOAD_STATUS = "upload_status"

        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_INTERVAL = "update_interval"
        const val KEY_HIGH_ACCURACY = "high_accuracy"

        const val DEFAULT_DEVICE_NAME = "Phone-t5rstp"
        const val DEFAULT_INTERVAL = 10
        const val DEFAULT_HIGH_ACCURACY = true
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager

    private val executor = Executors.newSingleThreadExecutor()

private var isStationary = false
private var stationaryReadings = 0
private var movementReadings = 0

private var referenceLocation: Location? = null

private var lastLocationRequestTime = 0L

private val stationaryInterval = 120_000L
private val stationaryThreshold = 30f
private val maximumAccuracy = 50f

private fun createLocationRequest(
    intervalOverride: Long? = null
): LocationRequest {
    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

val interval = intervalOverride ?: (
    prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL) * 1000L
)

    val highAccuracy = prefs.getBoolean(
        KEY_HIGH_ACCURACY,
        DEFAULT_HIGH_ACCURACY
    )

    val priority = if (highAccuracy) {
        Priority.PRIORITY_HIGH_ACCURACY
    } else {
        Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }

    return LocationRequest.Builder(
        priority,
        interval
    )
        .setMinUpdateIntervalMillis(interval / 2)
        .build()
}

private fun changeLocationInterval(interval: Long) {
    fusedLocationClient.removeLocationUpdates(locationCallback)

    fusedLocationClient.requestLocationUpdates(
        createLocationRequest(interval),
        locationCallback,
        Looper.getMainLooper()
    ).addOnFailureListener { error ->
        Log.e(TAG, "Failed to change GPS interval", error)
    }

    Log.d(TAG, "GPS interval changed to ${interval / 1000} seconds")
}

    private val locationCallback = object : LocationCallback() {

        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                handleLocation(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    .edit()
    .putBoolean(KEY_TRACKING, true)
    .apply()

        val notification = createNotification(
            "Waiting for GPS...",
            "Starting tracker"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startGpsUpdates()

        return START_STICKY
    }

    private fun startGpsUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission missing")
            stopSelf()
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                createLocationRequest(),
                locationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { error ->
                Log.e(TAG, "Location request failed", error)
                updateNotification(
                    "Location request failed",
                    error.message ?: "Unknown error"
                )
            }

            Log.d(TAG, "Fused GPS tracking started")

        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission error", e)
            stopSelf()
        }
    }

private fun hasMeaningfulMovement(
    previous: Location,
    current: Location
): Boolean {
    val distance = previous.distanceTo(current)

    val accuracyAllowance =
        previous.accuracy + current.accuracy

    val threshold = maxOf(
        stationaryThreshold,
        accuracyAllowance
    )

    return distance > threshold
}

private fun hasGoodAccuracy(location: Location): Boolean {
    return location.hasAccuracy() &&
        location.accuracy <= maximumAccuracy
}

private fun handleLocation(location: Location) {
    val lat = location.latitude
    val lon = location.longitude

    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .edit()
        .putString(KEY_LATITUDE, lat.toString())
        .putString(KEY_LONGITUDE, lon.toString())
        .putFloat(KEY_ACCURACY, location.accuracy)
        .apply()

    Log.d(TAG, "GPS: $lat, $lon accuracy: ${location.accuracy}")

    if (!hasGoodAccuracy(location)) {
        Log.d(TAG, "Uncertain GPS reading")

        if (isStationary) {
            changeLocationInterval(getSelectedInterval())
            Log.d(TAG, "Requesting faster GPS check")
        }

        return
    }

    val reference = referenceLocation

    if (reference == null) {
        referenceLocation = Location(location)
        uploadLocation(lat, lon)
        return
    }

    if (hasMeaningfulMovement(reference, location)) {
        movementReadings++
        stationaryReadings = 0

        if (isStationary && movementReadings == 1) {
            changeLocationInterval(getSelectedInterval())
        }

        if (movementReadings >= 2) {
            isStationary = false
            referenceLocation = Location(location)
            movementReadings = 0

            changeLocationInterval(getSelectedInterval())

            Log.d(TAG, "Movement confirmed")
            uploadLocation(lat, lon)
        } else {
            Log.d(TAG, "Possible movement: confirming")
        }

    } else {
        val wasCheckingMovement = movementReadings > 0

        movementReadings = 0
        stationaryReadings++

        if (isStationary && wasCheckingMovement) {
            changeLocationInterval(stationaryInterval)
        }

        if (!isStationary && stationaryReadings >= 3) {
            isStationary = true
            referenceLocation = Location(location)

            changeLocationInterval(stationaryInterval)

            Log.d(TAG, "Stationary mode activated")
        }
    }

if (isStationary) {
    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .edit()
        .putString(KEY_UPLOAD_STATUS, "Waiting")
        .apply()

    Log.d(TAG, "Stationary: skipping upload")
}
}

private fun getSelectedInterval(): Long {
    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    return prefs.getInt(
        KEY_INTERVAL,
        DEFAULT_INTERVAL
    ) * 1000L
}

private fun uploadLocation(lat: Double, lon: Double) {
    updateNotification(
        "$lat, $lon",
        "Sending coordinates..."
    )

    executor.execute {
        sendLocation(lat, lon)
    }
}

    private fun sendLocation(lat: Double, lon: Double) {
        var connection: HttpURLConnection? = null

        try {
            connection = URL(API_URL).openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

val deviceName = prefs.getString(
    KEY_DEVICE_NAME,
    DEFAULT_DEVICE_NAME
) ?: DEFAULT_DEVICE_NAME

val json = JSONObject().apply {
    put("id", deviceName)
    put("lat", lat)
    put("lon", lon)
}

getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    .edit()
    .putString(KEY_UPLOAD_STATUS, "Sending")
    .apply()

            connection.outputStream.use { output ->
                output.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode

            if (responseCode in 200..299) {
                Log.d(TAG, "Location uploaded successfully")

getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    .edit()
    .putLong(KEY_LAST_UPLOAD, System.currentTimeMillis())
    .putBoolean(KEY_SERVER_CONNECTED, true)
    .putString(KEY_UPLOAD_STATUS, "Uploaded")
    .apply()

                updateNotification(
                    "$lat, $lon",
                    "Server connected"
                )
            } else {
                Log.e(TAG, "Upload failed: HTTP $responseCode")

getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    .edit()
    .putBoolean(KEY_SERVER_CONNECTED, false)
    .putString(KEY_UPLOAD_STATUS, "Failed")
    .apply()

                updateNotification(
                    "$lat, $lon",
                    "Upload failed: HTTP $responseCode"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Location upload error", e)

getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    .edit()
    .putBoolean(KEY_SERVER_CONNECTED, false)
    .putString(KEY_UPLOAD_STATUS, "Failed")
    .apply()

            updateNotification(
                "$lat, $lon",
                "Upload error: ${e.message ?: "Connection failed"}"
            )

        } finally {
            connection?.disconnect()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Tracking",
                NotificationManager.IMPORTANCE_LOW
            )

            notificationManager.createNotificationChannel(channel)
        }
    }

private fun createNotification(
    coordinates: String,
    status: String
): Notification {

    val stopIntent = Intent(this, StopGpsReceiver::class.java)

    val stopPendingIntent = PendingIntent.getBroadcast(
        this,
        100,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("GPS Tracker")
        .setContentText(status)
        .setStyle(
            Notification.BigTextStyle()
                .bigText("$status\n$coordinates")
        )
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .addAction(
            android.R.drawable.ic_media_pause,
            "STOP",
            stopPendingIntent
        )
        .build()
}

    private fun updateNotification(
        coordinates: String,
        status: String
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(coordinates, status)
        )
    }

    override fun onDestroy() {

getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    .edit()
    .putBoolean(KEY_TRACKING, false)
    .apply()

        super.onDestroy()

        fusedLocationClient.removeLocationUpdates(locationCallback)

        executor.shutdownNow()

        Log.d(TAG, "GPS tracking stopped")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
