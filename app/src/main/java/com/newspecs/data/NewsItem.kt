package com.newspecs.data

import android.graphics.Color

data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val sourceUrl: String,
    val pubDateMs: Long
) {
    val shortSource: String get() = toShortName(source)
    val sourceDomain: String get() = extractDomain(sourceUrl)
    val sourceColor: Int get() = toColor(source)
    val timeAgo: String get() = toTimeAgo(pubDateMs)

    companion object {
        fun toShortName(source: String): String = when {
            source.contains("Manorama", true) -> "Manorama"
            source.contains("Asianet", true)  -> "Asianet"
            source.contains("News18", true)   -> "News18 ML"
            source.contains("Mathrubhumi", true) -> "Mathrubhumi"
            source.contains("Samayam", true)  -> "Samayam"
            source.contains("Express", true)  -> "IE ML"
            source.contains("Madhyamam", true) -> "Madhyamam"
            source.contains("Deepika", true)  -> "Deepika"
            source.contains("Janam", true)    -> "Janam TV"
            source.contains("Reporter", true) -> "Reporter"
            else -> source.take(14)
        }

        fun toColor(source: String): Int = when {
            source.contains("Manorama", true)    -> Color.parseColor("#D4826A")
            source.contains("Asianet", true)     -> Color.parseColor("#6AAAD4")
            source.contains("News18", true)      -> Color.parseColor("#8AB86A")
            source.contains("Mathrubhumi", true) -> Color.parseColor("#A882D4")
            source.contains("Samayam", true)     -> Color.parseColor("#D4B06A")
            source.contains("Express", true)     -> Color.parseColor("#6AB8B8")
            source.contains("Madhyamam", true)   -> Color.parseColor("#D48A6A")
            source.contains("Deepika", true)     -> Color.parseColor("#6AB8A8")
            else -> Color.parseColor("#8A8678")
        }

        fun toDomainForFavicon(source: String): String = when {
            source.contains("Manorama", true)    -> "manoramaonline.com"
            source.contains("Asianet", true)     -> "asianetnews.com"
            source.contains("News18", true)      -> "news18.com"
            source.contains("Mathrubhumi", true) -> "mathrubhumi.com"
            source.contains("Samayam", true)     -> "samayam.com"
            source.contains("Express", true)     -> "indianexpress.com"
            source.contains("Madhyamam", true)   -> "madhyamam.com"
            source.contains("Deepika", true)     -> "deepika.com"
            source.contains("Janam", true)       -> "janamtv.com"
            source.contains("Reporter", true)    -> "reporterlive.com"
            else -> extractDomain(source)
        }

        fun extractDomain(url: String): String = try {
            val noProto = url.removePrefix("https://").removePrefix("http://")
            noProto.split("/").first().removePrefix("www.")
        } catch (_: Exception) { "" }

        fun toTimeAgo(pubDateMs: Long): String {
            val diff = System.currentTimeMillis() - pubDateMs
            val mins = diff / 60_000
            return when {
                mins < 1    -> "just now"
                mins < 60   -> "${mins}m ago"
                mins < 1440 -> "${mins / 60}h ago"
                else        -> "${mins / 1440}d ago"
            }
        }
    }
}
