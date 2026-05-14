package com.newspecs.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    private fun saveToDisk(bmp: Bitmap, file: File) = try {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    } catch (_: Exception) { }
}
