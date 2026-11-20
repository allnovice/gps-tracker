package com.personal.gpstracker

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Switch
import android.widget.Toast

class MainActivity : ComponentActivity() {

private lateinit var statusText: TextView
private lateinit var toggleButton: Button
private lateinit var settingsButton: Button
private lateinit var deviceNameText: TextView
private lateinit var coordinatesText: TextView
private lateinit var lastUpdateText: TextView
private lateinit var accuracyText: TextView
private lateinit var batteryText: TextView
private lateinit var serverStatusText: TextView
private lateinit var uploadStatusText: TextView

private val handler = Handler(Looper.getMainLooper())

private val refreshUi = object : Runnable {
    override fun run() {
        updateInterface()
        handler.postDelayed(this, 1000)
    }
}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

statusText = TextView(this).apply {
    textSize = 22f
    setTextColor(getColor(R.color.success))
    setPadding(0, 0, 0, dp(8))
}

        toggleButton = Button(this).apply {
            setOnClickListener {
                toggleGpsTracking()
            }
        }

settingsButton = Button(this).apply {
    text = "SETTINGS"
    setOnClickListener {
        showSettings()
    }
}

deviceNameText = TextView(this).apply {
    text = "Device"
    textSize = 20f
    setTextColor(getColor(R.color.text_primary))
    setPadding(0, 0, 0, dp(12))
}

coordinatesText = TextView(this).apply {
    textSize = 18f
    setTextColor(getColor(R.color.text_primary))
    setPadding(0, 0, 0, dp(12))
}

lastUpdateText = TextView(this).apply {
    textSize = 14f
    setTextColor(getColor(R.color.text_secondary))
}

accuracyText = TextView(this).apply {
    textSize = 14f
    setTextColor(getColor(R.color.text_secondary))
}

batteryText = TextView(this).apply {
    textSize = 14f
    setTextColor(getColor(R.color.text_secondary))
}

serverStatusText = TextView(this).apply {
    textSize = 14f
    setTextColor(getColor(R.color.text_secondary))
}

uploadStatusText = TextView(this).apply {
    textSize = 14f
    setTextColor(getColor(R.color.text_secondary))
}

val header = TextView(this).apply {
    text = "GPS Tracker"
    textSize = 28f
    setTextColor(getColor(R.color.text_primary))
}

val subtitle = TextView(this).apply {
    text = "Personal location monitoring"
    textSize = 14f
    setTextColor(getColor(R.color.text_secondary))
}

val statusCard = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(20), dp(20), dp(20))
    background = createCardBackground(getColor(R.color.card_bg))

    addView(statusText)
    addView(serverStatusText)
    addView(uploadStatusText)
}

val deviceCard = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(20), dp(20), dp(20))
    background = createCardBackground(getColor(R.color.card_bg))

    addView(deviceNameText)
    addView(batteryText)
}

val locationCard = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(20), dp(20), dp(20))
    background = createCardBackground(getColor(R.color.card_bg))

    addView(coordinatesText)
    addView(accuracyText)
    addView(lastUpdateText)
}

toggleButton.apply {
    textSize = 18f
    setTextColor(android.graphics.Color.WHITE)
    background = createCardBackground(getColor(R.color.primary))
    isAllCaps = false
    minHeight = dp(60)
}

settingsButton.apply {
    textSize = 14f
    setTextColor(getColor(R.color.primary))
    background = createCardBackground(getColor(R.color.card_bg))
    isAllCaps = false
    minHeight = dp(48)
}

val content = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(24), dp(20), dp(24))

    addView(header)
    addView(subtitle)
    addView(statusCard, LinearLayout.LayoutParams(
        -1, -2
    ).apply {
        topMargin = dp(24)
    })

    addView(deviceCard, LinearLayout.LayoutParams(
        -1, -2
    ).apply {
        topMargin = dp(16)
    })

    addView(locationCard, LinearLayout.LayoutParams(
        -1, -2
    ).apply {
        topMargin = dp(16)
    })

    addView(toggleButton, LinearLayout.LayoutParams(
        -1, -2
    ).apply {
        topMargin = dp(24)
    })

    addView(settingsButton)
}

val layout = ScrollView(this).apply {
    setBackgroundColor(getColor(R.color.screen_bg))
    addView(content)
}

layout.setOnApplyWindowInsetsListener { _, insets ->

    val topInset: Int
    val bottomInset: Int

    if (Build.VERSION.SDK_INT >= 30) {
        val systemBars = insets.getInsets(
            WindowInsets.Type.systemBars()
        )

        topInset = systemBars.top
        bottomInset = systemBars.bottom
    } else {
        topInset = insets.systemWindowInsetTop
        bottomInset = insets.systemWindowInsetBottom
    }

    content.setPadding(
        dp(20),
        dp(24) + topInset,
        dp(20),
        dp(24) + bottomInset
    )

    insets
}

        setContentView(layout)

        updateInterface()

        requestNotificationPermission()
    }

private fun dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private fun createCardBackground(color: Int): android.graphics.drawable.GradientDrawable {
    return android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = 20f * resources.displayMetrics.density
    }
}

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
    }

private val locationPermissionRequest =
    registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val fineLocationGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (fineLocationGranted) {
            toggleGpsTracking()
        } else {
            statusText.text = "Precise location permission required"
        }
    }

private fun toggleGpsTracking() {
    val prefs = getSharedPreferences(
        GpsService.PREFS_NAME,
        MODE_PRIVATE
    )

    val isTracking = prefs.getBoolean(
        GpsService.KEY_TRACKING,
        false
    )

    if (!isTracking) {
        
if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    != PackageManager.PERMISSION_GRANTED
) {
    locationPermissionRequest.launch(
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    return
}

        val intent = Intent(this, GpsService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        prefs.edit()
            .putBoolean(GpsService.KEY_TRACKING, true)
            .apply()

    } else {
        stopService(Intent(this, GpsService::class.java))

        prefs.edit()
            .putBoolean(GpsService.KEY_TRACKING, false)
            .apply()
    }

    updateInterface()
}

private fun updateInterface() {
    val prefs = getSharedPreferences(
        GpsService.PREFS_NAME,
        MODE_PRIVATE
    )

    val isTracking = prefs.getBoolean(
        GpsService.KEY_TRACKING,
        false
    )

    val deviceName = prefs.getString(
        GpsService.KEY_DEVICE_NAME,
        GpsService.DEFAULT_DEVICE_NAME
    )

    val latitude = prefs.getString(GpsService.KEY_LATITUDE, "--")
    val longitude = prefs.getString(GpsService.KEY_LONGITUDE, "--")
    val accuracy = prefs.getFloat(GpsService.KEY_ACCURACY, 0f)
    val lastUpload = prefs.getLong(GpsService.KEY_LAST_UPLOAD, 0L)
    val serverConnected = prefs.getBoolean(
        GpsService.KEY_SERVER_CONNECTED,
        false
    )

val uploadStatus = prefs.getString(
    GpsService.KEY_UPLOAD_STATUS,
    "Waiting"
)

if (isTracking) {
    statusText.text = "● GPS Tracking Active"
    statusText.setTextColor(getColor(R.color.success))
    toggleButton.text = "Stop GPS"
} else {
    statusText.text = "● GPS Tracking Stopped"
    statusText.setTextColor(getColor(R.color.danger))
    toggleButton.text = "Start GPS"
}

    deviceNameText.text = deviceName
    coordinatesText.text = "$latitude, $longitude"

    accuracyText.text = if (accuracy > 0) {
        "%.1f meters".format(accuracy)
    } else {
        "--"
    }

    lastUpdateText.text = if (lastUpload > 0) {
        java.text.SimpleDateFormat(
            "MMM dd, yyyy HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date(lastUpload))
    } else {
        "No successful upload yet"
    }

    serverStatusText.text = if (serverConnected) {
        "Server connected"
    } else {
        "Server disconnected"
    }

uploadStatusText.text = when (uploadStatus) {
    "Sending" -> "Sending coordinates..."
    "Uploaded" -> "Coordinates uploaded"
    "Failed" -> "Upload failed"
    else -> "Waiting for next update"
}

val battery = getBatteryLevel()

batteryText.text = if (battery >= 0) {
    "$battery%"
} else {
    "Unavailable"
}

}

private fun getBatteryLevel(): Int {
    val batteryStatus = registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )

    val level = batteryStatus?.getIntExtra(
        BatteryManager.EXTRA_LEVEL,
        -1
    ) ?: -1

    val scale = batteryStatus?.getIntExtra(
        BatteryManager.EXTRA_SCALE,
        -1
    ) ?: -1

    return if (level >= 0 && scale > 0) {
        (level * 100) / scale
    } else {
        -1
    }
}

private fun showSettings() {
    val prefs = getSharedPreferences(
        GpsService.PREFS_NAME,
        MODE_PRIVATE
    )

    val settingsLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 20, 40, 10)
    }

    val deviceName = EditText(this).apply {
        hint = "Device name"
        setSingleLine(true)
        setText(
            prefs.getString(
                GpsService.KEY_DEVICE_NAME,
                GpsService.DEFAULT_DEVICE_NAME
            )
        )
    }

    val intervalLabel = TextView(this).apply {
        text = "Update interval"
        textSize = 16f
    }

    val intervals = listOf(5, 10, 30, 60)

    val intervalSpinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            intervals.map { "$it seconds" }
        )

        setSelection(
            intervals.indexOf(
                prefs.getInt(
                    GpsService.KEY_INTERVAL,
                    GpsService.DEFAULT_INTERVAL
                )
            ).coerceAtLeast(0)
        )
    }

    val accuracySwitch = Switch(this).apply {
        text = "High location accuracy"
        isChecked = prefs.getBoolean(
            GpsService.KEY_HIGH_ACCURACY,
            GpsService.DEFAULT_HIGH_ACCURACY
        )
    }

    settingsLayout.addView(deviceName)
    settingsLayout.addView(intervalLabel)
    settingsLayout.addView(intervalSpinner)
    settingsLayout.addView(accuracySwitch)

    android.app.AlertDialog.Builder(this)
        .setTitle("GPS Settings")
        .setView(settingsLayout)
        .setPositiveButton("SAVE") { _, _ ->
            prefs.edit()
                .putString(
                    GpsService.KEY_DEVICE_NAME,
                    deviceName.text.toString().trim()
                        .ifEmpty { GpsService.DEFAULT_DEVICE_NAME }
                )
                .putInt(
                    GpsService.KEY_INTERVAL,
                    intervals[intervalSpinner.selectedItemPosition]
                )
                .putBoolean(
                    GpsService.KEY_HIGH_ACCURACY,
                    accuracySwitch.isChecked
                )
                .apply()

            Toast.makeText(
                this,
                "Settings saved",
                Toast.LENGTH_SHORT
            ).show()
        }
        .setNegativeButton("CANCEL", null)
        .show()
}

override fun onResume() {
    super.onResume()
    handler.post(refreshUi)
}

override fun onPause() {
    handler.removeCallbacks(refreshUi)
    super.onPause()
}

}
