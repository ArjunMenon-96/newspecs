package com.newspecs.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.newspecs.R
import com.newspecs.data.FaviconLoader
import com.newspecs.data.NewsCache
import com.newspecs.data.NewsFetcher
import com.newspecs.data.NewsItem
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
        private const val MAX_ROWS      = 12   // (900-48-30)/68 ≈ 12

        fun calculateRows(heightDp: Int): Int =
            maxOf((heightDp - HEADER_DP - FOOTER_DP) / ROW_HEIGHT_DP, MIN_ROWS)
                .coerceAtMost(MAX_ROWS)

        /**
         * Two-phase update — called on a background thread from NewsRefreshService.
         *
         * Phase 1: push cached data immediately (no network, uses disk-cached favicons).
         * Phase 2: fetch RSS, save, pre-warm favicon cache, push again with live data.
         *
         * No RemoteViewsService / ListView / notifyAppWidgetViewDataChanged involved.
         * News is injected directly via RemoteViews.addView() — works on every launcher.
         */
        fun triggerUpdate(context: Context, explicitIds: IntArray? = null) {
            val ctx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = (explicitIds?.takeIf { it.isNotEmpty() }
                ?: mgr.getAppWidgetIds(ComponentName(ctx, NewsWidget::class.java)))
                .also { if (it.isEmpty()) return }

            // Phase 1 — cached data, disk-only favicons, instant
            pushViews(ctx, mgr, ids)

            // Phase 2 — network fetch
            val news = NewsFetcher.fetch()
            if (news.isNotEmpty()) {
                NewsCache.save(ctx, news)
                NewsCache.saveRefreshTime(ctx)
                // Pre-warm favicon disk cache so buildListViews can use them without network
                news.forEach { item ->
                    FaviconLoader.get(ctx, NewsItem.toDomainForFavicon(item.source))
                }
                pushViews(ctx, mgr, ids)
            }
        }

        private fun pushViews(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            for (id in ids) {
                val views = buildViews(ctx, id, mgr.getAppWidgetOptions(id))
                mgr.updateAppWidget(id, views)
                // No notifyAppWidgetViewDataChanged — we no longer use RemoteViewsService
            }
        }

        fun buildViews(context: Context, widgetId: Int, opts: Bundle): RemoteViews {
            val widthDp  = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,   320)
            // MAX_HEIGHT = portrait height (the one that grows when the user drags taller).
            // MIN_HEIGHT = landscape height — always smaller, wrong value for row calculation.
            val heightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,  180)
            return if (widthDp < 150 || heightDp < 100) buildSingleViews(context, widgetId)
            else buildListViews(context, widgetId, heightDp)
        }

        /**
         * Builds the full-size widget RemoteViews.
         *
         * Items are added directly to news_container via addView() — no ListView,
         * no RemoteViewsService binding, no factory. Each row is a RemoteViews
         * inflated from widget_item.xml with content set inline.
         *
         * Favicons use disk-cache only (getFromDiskCacheOnly) so this is safe to
         * call on any thread, including the main thread for Phase 1 pushes.
         */
        private fun buildListViews(context: Context, widgetId: Int, heightDp: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val rows  = calculateRows(heightDp)
            val news  = NewsCache.load(context).take(rows)

            // Header
            views.setTextViewText(R.id.widget_name, "newspecs")
            views.setTextViewText(R.id.update_time, currentTime())
            views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPi(context))
            views.setOnClickPendingIntent(R.id.reload_btn, manualRefreshPi(context))

            // Footer
            views.setTextViewText(R.id.story_count, "${news.size} stories · <24h")
            val remaining = ((NewsCache.getRefreshTime(context) + INTERVAL_MS) - System.currentTimeMillis())
                .coerceAtLeast(0L)
            val m = remaining / 60_000
            val s = (remaining % 60_000) / 1000
            views.setTextViewText(R.id.next_refresh, "↻ %d:%02d".format(m, s))

            // Clear any rows from a previous reapply — addView() appends, not replaces,
            // so without this call each updateAppWidget doubles up the rows.
            views.removeAllViews(R.id.news_container)

            // Empty state
            if (news.isEmpty()) {
                views.setViewVisibility(R.id.news_container, View.GONE)
                views.setViewVisibility(R.id.empty_view, View.VISIBLE)
                return views
            }

            views.setViewVisibility(R.id.empty_view, View.GONE)
            views.setViewVisibility(R.id.news_container, View.VISIBLE)

            // Inject each news item as a RemoteViews row directly into the container
            news.forEachIndexed { i, item ->
                val row = RemoteViews(context.packageName, R.layout.widget_item)

                row.setTextViewText(R.id.headline, item.title)
                row.setTextColor(R.id.headline, Color.parseColor("#B8B4A6"))

                row.setTextViewText(R.id.source_tag, item.shortSource)
                row.setTextColor(R.id.source_tag, item.sourceColor)
                row.setInt(R.id.source_tag, "setBackgroundResource", tagBgRes(item.source))

                row.setTextViewText(R.id.time_ago, item.timeAgo)
                row.setTextColor(R.id.time_ago, Color.parseColor("#3E3E38"))

                // Favicon — disk cache only, no network (safe on any thread)
                val domain  = NewsItem.toDomainForFavicon(item.source)
                val favicon = FaviconLoader.getFromDiskCacheOnly(context, domain)
                    ?: FaviconLoader.createPlaceholder(item.source, item.sourceColor)
                row.setImageViewBitmap(R.id.favicon, favicon)

                // Tap opens the article URL
                val pi = PendingIntent.getActivity(
                    context,
                    widgetId * MAX_ROWS + i,
                    Intent(Intent.ACTION_VIEW, Uri.parse(item.link)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                row.setOnClickPendingIntent(R.id.item_root, pi)

                views.addView(R.id.news_container, row)
            }

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

        /** Maps source name to its per-source static tag drawable (colored stroke border). */
        private fun tagBgRes(source: String): Int = when {
            source.contains("Manorama",    true) -> R.drawable.tag_bg_manorama
            source.contains("Asianet",     true) -> R.drawable.tag_bg_asianet
            source.contains("News18",      true) -> R.drawable.tag_bg_news18
            source.contains("Mathrubhumi", true) -> R.drawable.tag_bg_mathrubhumi
            source.contains("Samayam",     true) -> R.drawable.tag_bg_samayam
            source.contains("Express",     true) -> R.drawable.tag_bg_ie
            else                                 -> R.drawable.tag_bg_default
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
        val ctx = context.applicationContext
        pushViews(ctx, mgr, ids)
        ContextCompat.startForegroundService(ctx, Intent(ctx, NewsRefreshService::class.java))
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, mgr: AppWidgetManager, widgetId: Int, newOpts: Bundle
    ) {
        // Rebuild with updated dimensions — no service start needed, just redraw from cache
        mgr.updateAppWidget(widgetId, buildViews(context, widgetId, newOpts))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            ACTION_AUTO_REFRESH,
            ACTION_MANUAL_REFRESH -> {
                val ctx = context.applicationContext
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?.takeIf { it.isNotEmpty() }
                    ?: mgr.getAppWidgetIds(ComponentName(ctx, NewsWidget::class.java))

                if (ids.isEmpty()) return

                // Phase 1 — push cached data now, synchronously on the main thread
                pushViews(ctx, mgr, ids)

                // Phase 2 — ForegroundService handles network fetch (no time limit)
                ContextCompat.startForegroundService(
                    ctx, Intent(ctx, NewsRefreshService::class.java)
                )
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context)  { scheduleRefresh(context) }
    override fun onDisabled(context: Context) { cancelRefresh(context) }
}
