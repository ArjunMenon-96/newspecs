package com.newspecs.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

// Reschedules the 5-min alarm after device reboot so widgets keep refreshing
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, NewsWidget::class.java))
        if (ids.isNotEmpty()) {
            NewsWidget.scheduleRefresh(context)
            NewsWidget.triggerUpdate(context)
        }
    }
}
