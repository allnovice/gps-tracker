package com.personal.gpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(
            GpsService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val isTracking = prefs.getBoolean(
            GpsService.KEY_TRACKING,
            false
        )

        if (isTracking) {
            val serviceIntent = Intent(context, GpsService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
