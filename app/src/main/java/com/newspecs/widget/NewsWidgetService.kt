package com.newspecs.widget

import android.content.Intent
import android.widget.RemoteViewsService

class NewsWidgetService : RemoteViewsService() {

    companion object {
        const val EXTRA_MAX_ROWS = "max_rows"
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1
        )
        val maxRows = intent.getIntExtra(EXTRA_MAX_ROWS, 10)
        return NewsWidgetFactory(applicationContext, widgetId, maxRows)
    }
}
