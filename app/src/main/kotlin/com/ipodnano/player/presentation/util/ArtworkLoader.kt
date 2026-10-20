package com.ipodnano.player.presentation.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.Executors

object ArtworkLoader {

    private const val MAX_CACHE_SIZE = 20
    // Memory-efficient LRU cache that recycles evicted bitmaps to avoid heap leaks
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bitmap>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
                if (size > MAX_CACHE_SIZE) {
                    try { eldest?.value?.recycle() } catch (_: Exception) {}
                    return true
                }
                return false
            }
        }
    )

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadArtwork(
        context: Context,
        uriOrUrl: String?,
        onLoaded: (Bitmap) -> Unit
    ): Bitmap? {
        if (uriOrUrl.isNullOrEmpty()) return null

        val cached = cache[uriOrUrl]
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        executor.execute {
            var bitmap: Bitmap? = null
            try {
                if (uriOrUrl.startsWith("http://") || uriOrUrl.startsWith("https://")) {
                    val url = URL(uriOrUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 7000
                    conn.doInput = true
                    conn.connect()
                    if (conn.responseCode in 200..399) {
                        var stream: InputStream = conn.inputStream
                        if (conn.responseCode != 200) {
                            val redirectUrl = conn.getHeaderField("Location")
                            if (!redirectUrl.isNullOrEmpty()) {
                                val redConn = URL(redirectUrl).openConnection() as HttpURLConnection
                                redConn.connectTimeout = 5000
                                redConn.readTimeout = 7000
                                stream = redConn.inputStream
                            }
                        }
                        // Sample down to prevent excessive memory usage
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 1
                            inPreferredConfig = Bitmap.Config.RGB_565 // 50% memory savings compared to ARGB_8888
                        }
                        bitmap = BitmapFactory.decodeStream(stream, null, options)
                        stream.close()
                    }
                    conn.disconnect()
                } else if (uriOrUrl.startsWith("content://") || uriOrUrl.startsWith("file://")) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, Uri.parse(uriOrUrl))
                        val picture = retriever.embeddedPicture
                        retriever.release()
                        if (picture != null) {
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                            bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            if (bitmap != null) {
                cache[uriOrUrl] = bitmap
                mainHandler.post {
                    onLoaded(bitmap)
                }
            }
        }

        return null
    }

    fun clearCache() {
        synchronized(cache) {
            for (bmp in cache.values) {
                try { bmp.recycle() } catch (_: Exception) {}
            }
            cache.clear()
        }
    }
}
