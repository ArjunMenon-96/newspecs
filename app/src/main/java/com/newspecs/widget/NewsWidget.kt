package com.newspecs.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import com.newspecs.R
import com.newspecs.data.NewsCache
import com.newspecs.data.NewsFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewsWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_AUTO_REFRESH   = "com.newspecs.AUTO_REFRESH"
        const val ACTION_MANUAL_REFRESH = "com.newspecs.MANUAL_REFRESH"
        private const val RC_REFRESH    = 1001
        const val INTERVAL_MS           = 5 * 60 * 1000L

        private const val ROW_HEIGHT_DP = 68
        private const val HEADER_DP     = 48
        private const val FOOTER_DP     = 30
        private const val MIN_ROWS      = 5

        fun calculateRows(heightDp: Int): Int =
            maxOf((heightDp - HEADER_DP - FOOTER_DP) / ROW_HEIGHT_DP, MIN_ROWS)

        /**
         * Two-phase update:
         * 1. Immediately push cached data to the widget (so it never stays blank)
         * 2. Fetch fresh data, save, then push again
         *
         * Must be called from a background thread. Always uses applicationContext
         * so it's safe to call from a BroadcastReceiver coroutine.
         */
        fun triggerUpdate(context: Context, explicitIds: IntArray? = null) {
            val ctx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = (explicitIds?.takeIf { it.isNotEmpty() }
                ?: mgr.getAppWidgetIds(ComponentName(ctx, NewsWidget::class.java)))
                .also { if (it.isEmpty()) return }

            // Phase 1 — show whatever is already cached (instant, no network)
            pushViews(ctx, mgr, ids)

            // Phase 2 — fetch, save, push again with fresh data
            val news = NewsFetcher.fetch()
            if (news.isNotEmpty()) {
                NewsCache.save(ctx, news)
                NewsCache.saveRefreshTime(ctx)
                pushViews(ctx, mgr, ids)
            }
        }

        private fun pushViews(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            for (id in ids) {
                val views = buildViews(ctx, id, mgr.getAppWidgetOptions(id))
                mgr.updateAppWidget(id, views)
                mgr.notifyAppWidgetViewDataChanged(id, R.id.news_list)
            }
        }

        fun buildViews(context: Context, widgetId: Int, opts: Bundle): RemoteViews {
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            return if (minW < 150 || minH < 100) buildSingleViews(context, widgetId)
            else buildListViews(context, widgetId, minH)
        }

        private fun buildListViews(context: Context, widgetId: Int, heightDp: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val news  = NewsCache.load(context)
            val rows  = calculateRows(heightDp)

            val svcIntent = Intent(context, NewsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(NewsWidgetService.EXTRA_MAX_ROWS, rows)
                data = Uri.parse("newspecs://widget/$widgetId/$rows")
            }
            views.setRemoteAdapter(R.id.news_list, svcIntent)
            views.setEmptyView(R.id.news_list, R.id.empty_view)

            val itemTemplate = PendingIntent.getActivity(
                context, 0,
                Intent(Intent.ACTION_VIEW),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.news_list, itemTemplate)

            views.setTextViewText(R.id.widget_name, "newspecs")
            views.setTextViewText(R.id.update_time, currentTime())
            views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPi(context))
            views.setOnClickPendingIntent(R.id.reload_btn, manualRefreshPi(context))

            val count = minOf(news.size, rows)
            views.setTextViewText(R.id.story_count, "$count stories · <24h")
            val remaining = ((NewsCache.getRefreshTime(context) + INTERVAL_MS) - System.currentTimeMillis())
                .coerceAtLeast(0L)
            val m = remaining / 60_000
            val s = (remaining % 60_000) / 1000
            views.setTextViewText(R.id.next_refresh, "Refresh in %d:%02d".format(m, s))

            return views
        }

        private fun buildSingleViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_small)
            val news  = NewsCache.load(context)
            views.setTextViewText(R.id.widget_name, "newspecs")
            if (news.isNotEmpty()) {
                val top = news[0]
                views.setTextViewText(R.id.headline, top.title)
                views.setTextViewText(R.id.source_tag, top.shortSource)
                views.setTextViewText(R.id.time_ago, top.timeAgo)
                val pi = PendingIntent.getActivity(
                    context, widgetId,
                    Intent(Intent.ACTION_VIEW, Uri.parse(top.link)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.headline, pi)
            }
            views.setOnClickPendingIntent(R.id.reload_btn, manualRefreshPi(context))
            return views
        }

        fun scheduleRefresh(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                refreshPi(context)
            )
        }

        fun cancelRefresh(context: Context) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .cancel(refreshPi(context))
        }

        private fun refreshPi(context: Context) = PendingIntent.getBroadcast(
            context, RC_REFRESH,
            Intent(context, NewsWidget::class.java).apply { action = ACTION_AUTO_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun manualRefreshPi(context: Context) = PendingIntent.getBroadcast(
            context, RC_REFRESH + 1,
            Intent(context, NewsWidget::class.java).apply { action = ACTION_MANUAL_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun currentTime(): String =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Handled in onReceive using goAsync so Android doesn't kill the process mid-fetch
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, mgr: AppWidgetManager, widgetId: Int, newOpts: Bundle
    ) {
        val views = buildViews(context, widgetId, newOpts)
        mgr.updateAppWidget(widgetId, views)
        mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.news_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            ACTION_AUTO_REFRESH,
            ACTION_MANUAL_REFRESH -> {
                // Extract IDs from the intent when present — more reliable than
                // getAppWidgetIds() at first-placement time
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                val appCtx = context.applicationContext
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { triggerUpdate(appCtx, ids) } finally { pending.finish() }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context)  { scheduleRefresh(context) }
    override fun onDisabled(context: Context) { cancelRefresh(context) }
}
