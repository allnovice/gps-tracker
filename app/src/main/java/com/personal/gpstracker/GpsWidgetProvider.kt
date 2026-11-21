package com.personal.gpstracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast

class GpsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, GpsWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)

            for (widgetId in widgetIds) {
                updateWidget(context, manager, widgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val prefs = context.getSharedPreferences(
                GpsService.PREFS_NAME,
                Context.MODE_PRIVATE
            )

            val isTracking = prefs.getBoolean(
                GpsService.KEY_TRACKING,
                true
            )

            val realtimeUntil = prefs.getLong(
                GpsService.KEY_REALTIME_UNTIL,
                0L
            )

            val isRealtime = prefs.getBoolean(
                GpsService.KEY_REALTIME,
                false
            ) && realtimeUntil > System.currentTimeMillis()

            val views = RemoteViews(
                context.packageName,
                R.layout.gps_widget
            )

            val activeColor = Color.parseColor("#A78BFA")
            val inactiveColor = Color.parseColor("#77717F")
            val openColor = Color.parseColor("#D8D1E8")

            views.setInt(
                R.id.widget_gps_button,
                "setColorFilter",
                if (isTracking) activeColor else inactiveColor
            )

            views.setInt(
                R.id.widget_realtime_button,
                "setColorFilter",
                if (isRealtime) activeColor else inactiveColor
            )

            views.setInt(
                R.id.widget_open_button,
                "setColorFilter",
                openColor
            )

            views.setOnClickPendingIntent(
                R.id.widget_gps_button,
                servicePendingIntent(
                    context,
                    if (isTracking) {
                        GpsService.ACTION_STOP_TRACKING
                    } else {
                        GpsService.ACTION_START_TRACKING
                    },
                    100
                )
            )

            views.setOnClickPendingIntent(
                R.id.widget_realtime_button,
                servicePendingIntent(
                    context,
                    if (isRealtime) {
                        GpsService.ACTION_STOP_REALTIME
                    } else {
                        GpsService.ACTION_START_REALTIME
                    },
                    200
                )
            )

            val openIntent = Intent(context, MainActivity::class.java)

            val openPendingIntent = PendingIntent.getActivity(
                context,
                300,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(
                R.id.widget_open_button,
                openPendingIntent
            )

            manager.updateAppWidget(widgetId, views)
        }

        private fun servicePendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {

            val intent = Intent(context, GpsService::class.java).apply {
                this.action = action
            }

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )
            }
        }
    }
}
