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

        views.setTextViewText(R.id.headline, item.title)
        views.setTextColor(R.id.headline, Color.parseColor("#B8B4A6"))

        views.setTextViewText(R.id.source_tag, item.shortSource)
        views.setTextColor(R.id.source_tag, item.sourceColor)
        // Use a static per-source drawable so the colored stroke border is preserved
        // (setBackgroundColor via reflection replaces the drawable, losing the stroke)
        views.setInt(R.id.source_tag, "setBackgroundResource", tagBgRes(item.source))

        views.setTextViewText(R.id.time_ago, item.timeAgo)
        views.setTextColor(R.id.time_ago, Color.parseColor("#3E3E38"))

        val domain  = NewsItem.toDomainForFavicon(item.source)
        val favicon = FaviconLoader.get(context, domain)
            ?: FaviconLoader.createPlaceholder(item.source, item.sourceColor)
        views.setImageViewBitmap(R.id.favicon, favicon)

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

    private fun tagBgRes(source: String): Int = when {
        source.contains("Manorama",    true) -> R.drawable.tag_bg_manorama
        source.contains("Asianet",     true) -> R.drawable.tag_bg_asianet
        source.contains("News18",      true) -> R.drawable.tag_bg_news18
        source.contains("Mathrubhumi", true) -> R.drawable.tag_bg_mathrubhumi
        source.contains("Samayam",     true) -> R.drawable.tag_bg_samayam
        source.contains("Express",     true) -> R.drawable.tag_bg_ie
        else                                 -> R.drawable.tag_bg_default
    }
}
