package com.newspecs.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.newspecs.R
import com.newspecs.data.FaviconLoader
import com.newspecs.data.NewsCache
import com.newspecs.data.NewsItem

class NewsWidgetFactory(
    private val context: Context,
    private val widgetId: Int,
    private val maxRows: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<NewsItem> = emptyList()

    override fun onCreate() { reload() }

    override fun onDataSetChanged() { reload() }

    override fun onDestroy() {}

    override fun getCount() = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_item)

        val views = RemoteViews(context.packageName, R.layout.widget_item)

        // Headline
        views.setTextViewText(R.id.headline, item.title)
        views.setTextColor(R.id.headline, Color.parseColor("#B8B4A6"))

        // Source tag text + color
        views.setTextViewText(R.id.source_tag, item.shortSource)
        views.setTextColor(R.id.source_tag, item.sourceColor)

        // Tag border tint via background — we overlay a semi-transparent version of the source color
        val tagBgColor = Color.argb(28,
            Color.red(item.sourceColor),
            Color.green(item.sourceColor),
            Color.blue(item.sourceColor)
        )
        views.setInt(R.id.source_tag, "setBackgroundColor", tagBgColor)

        // Time
        views.setTextViewText(R.id.time_ago, item.timeAgo)
        views.setTextColor(R.id.time_ago, Color.parseColor("#3E3E38"))

        // Favicon — try cache first, fall back to Kerala map vector
        val domain  = NewsItem.toDomainForFavicon(item.source)
        val favicon = FaviconLoader.get(context, domain)
        if (favicon != null) {
            views.setImageViewBitmap(R.id.favicon, favicon)
        } else {
            views.setImageViewResource(R.id.favicon, R.drawable.ic_kerala_map)
            views.setInt(R.id.favicon, "setColorFilter", item.sourceColor)
        }

        // Row tap — fill in the article URI so the PendingIntentTemplate resolves correctly
        val fillIn = Intent().apply { data = Uri.parse(item.link) }
        views.setOnClickFillInIntent(R.id.item_root, fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount() = 1

    override fun getItemId(position: Int) = items.getOrNull(position)?.pubDateMs ?: position.toLong()

    override fun hasStableIds() = true

    private fun reload() {
        items = NewsCache.load(context).take(maxRows)
    }
}
