package com.newspecs.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object FaviconLoader {

    private const val FAVICON_API = "https://www.google.com/s2/favicons?domain=%s&sz=32"
    private const val TIMEOUT_MS  = 8_000

    fun get(context: Context, domain: String): Bitmap? {
        if (domain.isBlank()) return null
        val cacheFile = File(context.cacheDir, "fav_${domain.replace(".", "_")}.png")
        if (cacheFile.exists() && cacheFile.lastModified() > System.currentTimeMillis() - 86_400_000) {
            return BitmapFactory.decodeFile(cacheFile.absolutePath)
        }
        return download(domain)?.let { bmp ->
            val processed = removeWhiteBg(bmp)
            saveToDisk(processed, cacheFile)
            processed
        }
    }

    private fun download(domain: String): Bitmap? = try {
        val url = FAVICON_API.format(domain)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
        }
        BitmapFactory.decodeStream(conn.inputStream).also { conn.disconnect() }
    } catch (_: Exception) { null }

    /**
     * Replaces white/near-white pixels with transparent so favicons look
     * clean on the dark widget background (equivalent to CSS screen blend mode).
     */
    private fun removeWhiteBg(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width; val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            if (Color.red(p) > 200 && Color.green(p) > 200 && Color.blue(p) > 200) {
                pixels[i] = Color.TRANSPARENT
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** Colored circle with the source's first letter — used when favicon isn't available. */
    fun createPlaceholder(source: String, color: Int, sizePx: Int = 48): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Semi-transparent filled circle
        paint.color = Color.argb(55, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // Source initial letter
        paint.color = color
        paint.textSize = sizePx * 0.46f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.MONOSPACE
        val letter = NewsItem.toShortName(source).firstOrNull()?.uppercaseChar()?.toString() ?: "N"
        val yPos = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, sizePx / 2f, yPos, paint)

        return bmp
    }

    private fun saveToDisk(bmp: Bitmap, file: File) = try {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    } catch (_: Exception) { }
}
