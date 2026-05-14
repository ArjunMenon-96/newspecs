package com.newspecs.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object NewsCache {
    private const val PREFS = "newspecs_cache"
    private const val KEY   = "news_json"

    fun save(context: Context, items: List<NewsItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("title",     item.title)
                put("link",      item.link)
                put("source",    item.source)
                put("sourceUrl", item.sourceUrl)
                put("pubDateMs", item.pubDateMs)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: Context): List<NewsItem> = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NewsItem(
                title     = o.getString("title"),
                link      = o.getString("link"),
                source    = o.getString("source"),
                sourceUrl = o.getString("sourceUrl"),
                pubDateMs = o.getLong("pubDateMs")
            )
        }
    } catch (_: Exception) { emptyList() }
}
