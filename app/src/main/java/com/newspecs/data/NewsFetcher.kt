package com.newspecs.data

import android.util.Xml
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

object NewsFetcher {

    private const val FEED_URL =
        "https://news.google.com/rss/search?q=kerala&hl=ml&gl=IN&ceid=IN:ml"
    private const val MAX_AGE_MS   = 24 * 60 * 60 * 1000L
    private const val TIMEOUT_MS   = 8_000  // must stay within goAsync 10s limit on API 31+
    private val DATE_FMT = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)

    fun fetch(): List<NewsItem> {
        val raw = download(FEED_URL) ?: return emptyList()
        val parsed = parseRss(raw)
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val fresh = parsed.filter { it.pubDateMs >= cutoff }
        return deduplicate(fresh).sortedByDescending { it.pubDateMs }
    }

    private fun download(urlStr: String): String? = try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        }
        conn.inputStream.bufferedReader().use { it.readText() }
            .also { conn.disconnect() }
    } catch (_: Exception) { null }

    private fun parseRss(xml: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        val parser = Xml.newPullParser()
        parser.setInput(xml.byteInputStream(), "UTF-8")

        var inItem  = false
        var title   = ""
        var link    = ""
        var source  = ""
        var srcUrl  = ""
        var pubDate = ""

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = ""; link = ""; source = ""; srcUrl = ""; pubDate = ""
                    }
                    "title"   -> if (inItem) title   = parser.nextText()
                    "link"    -> if (inItem) link    = safeNextText(parser)
                    "pubDate" -> if (inItem) pubDate = parser.nextText()
                    "source"  -> if (inItem) {
                        srcUrl = parser.getAttributeValue(null, "url") ?: ""
                        source = parser.nextText()
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    inItem = false
                    val cleanTitle = stripSourceSuffix(title, source)
                    val pubMs = parseDate(pubDate)
                    if (cleanTitle.isNotBlank() && link.isNotBlank() && pubMs > 0) {
                        items += NewsItem(cleanTitle, link, source, srcUrl, pubMs)
                    }
                }
            }
            eventType = parser.next()
        }
        return items
    }

    // <link> in some RSS feeds is a text-less empty tag; guard against it
    private fun safeNextText(parser: org.xmlpull.v1.XmlPullParser): String = try {
        parser.nextText()
    } catch (_: Exception) { "" }

    private fun stripSourceSuffix(title: String, source: String): String {
        // Google News appends " - Source Name" to every title
        val short = NewsItem.toShortName(source)
        return title
            .removeSuffix(" - $source")
            .removeSuffix(" - $short")
            .trim()
    }

    private fun parseDate(raw: String): Long = try {
        DATE_FMT.parse(raw.trim())?.time ?: 0L
    } catch (_: Exception) { 0L }

    /**
     * Deduplication: if two items share ≥60% of their first-10-word tokens,
     * keep the more recent one.
     */
    private fun deduplicate(items: List<NewsItem>): List<NewsItem> {
        val result = mutableListOf<NewsItem>()
        for (item in items) {
            val words = item.title.split(" ").take(10).toSet()
            val duplicate = result.indexOfFirst { existing ->
                val existingWords = existing.title.split(" ").take(10).toSet()
                val overlap = words.intersect(existingWords).size.toDouble()
                overlap / words.size.coerceAtLeast(1) >= 0.6
            }
            if (duplicate == -1) {
                result += item
            } else if (item.pubDateMs > result[duplicate].pubDateMs) {
                result[duplicate] = item
            }
        }
        return result
    }
}
