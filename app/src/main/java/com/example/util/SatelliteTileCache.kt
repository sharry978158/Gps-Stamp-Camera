package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class SatelliteTileKey(val z: Int, val x: Int, val y: Int, val isSatellite: Boolean = true)

object SatelliteTileCache {
    private val cache = LinkedHashMap<SatelliteTileKey, Bitmap>(48, 0.75f, true)

    @Synchronized
    fun get(key: SatelliteTileKey): Bitmap? = cache[key]

    @Synchronized
    fun put(key: SatelliteTileKey, bitmap: Bitmap) {
        if (cache.size > 54) {
            val eldest = cache.entries.firstOrNull()?.key
            if (eldest != null) cache.remove(eldest)
        }
        cache[key] = bitmap
    }

    fun lonToTileX(lon: Double, zoom: Int): Double {
        return ((lon + 180.0) / 360.0) * (1 shl zoom)
    }

    fun latToTileY(lat: Double, zoom: Int): Double {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val latRad = clampedLat * PI / 180.0
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

    fun centerTileIndices(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val tx = floor(lonToTileX(lon, zoom)).toInt()
        val ty = floor(latToTileY(lat, zoom)).toInt()
        return tx to ty
    }

    suspend fun fetchTile(zoom: Int, x: Int, y: Int, isSatellite: Boolean = true): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = SatelliteTileKey(zoom, x, y, isSatellite)
            get(key)?.let { return@withContext it }

            val urls = if (isSatellite) {
                listOf(
                    "https://mt1.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom",
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x",
                    "https://tile.openstreetmap.org/$zoom/$x/$y.png"
                )
            } else {
                listOf(
                    "https://mt1.google.com/vt/lyrs=m&x=$x&y=$y&z=$zoom",
                    "https://tile.openstreetmap.org/$zoom/$x/$y.png"
                )
            }

            for (urlStr in urls) {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL(urlStr)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                        setRequestProperty("User-Agent", "GPSMapCameraAndroid/2.0")
                    }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                        if (bmp != null) {
                            put(key, bmp)
                            return@withContext bmp
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    conn?.disconnect()
                }
            }
            null
        }
}
