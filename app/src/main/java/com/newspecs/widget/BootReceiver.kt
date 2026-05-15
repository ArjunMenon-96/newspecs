package com.newspecs.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val ctx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, NewsWidget::class.java))
        if (ids.isNotEmpty()) {
            NewsWidget.scheduleRefresh(ctx)
            // ForegroundService handles the fetch — no goAsync() needed
            ContextCompat.startForegroundService(ctx, Intent(ctx, NewsRefreshService::class.java))
        }
    }
}
