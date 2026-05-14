package com.newspecs.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, NewsWidget::class.java))
        if (ids.isNotEmpty()) {
            NewsWidget.scheduleRefresh(context)
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { NewsWidget.triggerUpdate(context) } finally { pending.finish() }
            }
        }
    }
}
