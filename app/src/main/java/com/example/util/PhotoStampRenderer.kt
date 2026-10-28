package com.example.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.CapturedPhotoItem
import com.example.model.GpsStampData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

object PhotoStampRenderer {

    fun createFallbackSceneBitmap(width: Int = 1440, height: Int = 1920): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height * 0.65f,
                intArrayOf(
                    Color.parseColor("#0A192F"),
                    Color.parseColor("#163859"),
                    Color.parseColor("#2A5C8A"),
                    Color.parseColor("#D9822B")
                ),
                floatArrayOf(0f, 0.45f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55FFB300")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(width * 0.72f, height * 0.42f, width * 0.16f, sunPaint)
        sunPaint.color = Color.parseColor("#CCFFD54F")
        canvas.drawCircle(width * 0.72f, height * 0.42f, width * 0.08f, sunPaint)

        val ridge1Path = Path().apply {
            moveTo(0f, height * 0.62f)
            lineTo(width * 0.22f, height * 0.48f)
            lineTo(width * 0.46f, height * 0.56f)
            lineTo(width * 0.74f, height * 0.44f)
            lineTo(width.toFloat(), height * 0.54f)
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        val ridge1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#13293D")
            style = Paint.Style.FILL
        }
        canvas.drawPath(ridge1Path, ridge1Paint)

        return bmp
    }

    fun rotateBitmapIfNeeded(source: Bitmap, rotationDegrees: Int, mirrorHorizontal: Boolean = false): Bitmap {
        if (rotationDegrees == 0 && !mirrorHorizontal) return source
        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        if (mirrorHorizontal) {
            matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f)
        }
        return try {
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated != source) {
                source.recycle()
            }
            rotated
        } catch (_: Exception) {
            source
        }
    }

    /**
     * Renders the exact 1:1 "GPS Map Camera" overlay from the user's screenshot onto the saved photo:
     * - Top-right `[📷 GPS Map Camera]` badge
     * - Left square rounded Satellite Map with red pin, blue beam, and "Google" watermark
     * - Right translucent dark box with Place Title, Indian Flag, Full Street Address,
     *   Lat/Long coordinates, Day + Date + Time + GMT offset, and Note.
     */
    suspend fun burnGpsStampOntoBitmap(
        source: Bitmap,
        stampData: GpsStampData,
        captureTimeMillis: Long
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val w = result.width.toFloat()
        val h = result.height.toFloat()
        val scale = min(w, h) / 1080f

        val marginSide = 44f * scale
        val marginBottom = 26f * scale
        val cardHeight = 290f * scale
        val cardBottom = h - marginBottom
        val cardTop = cardBottom - cardHeight

        // 1. Top-Right "GPS Map Camera" Badge right above the right card
        val badgeHeight = 46f * scale
        val badgeWidth = 265f * scale
        val badgeRight = w - marginSide - 4f * scale
        val badgeLeft = badgeRight - badgeWidth
        val badgeBottom = cardTop - 4f * scale
        val badgeTop = badgeBottom - badgeHeight
        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 26, 26, 26)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 12f * scale, 12f * scale, badgeBgPaint)

        // Mini camera icon inside badge
        val iconSize = 28f * scale
        val iconLeft = badgeLeft + 14f * scale
        val iconTop = badgeTop + (badgeHeight - iconSize) / 2f
        val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#29B6F6") }
        canvas.drawRoundRect(iconRect, 6f * scale, 6f * scale, iconBgPaint)
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B3A4B") }
        canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconSize * 0.35f, lensPaint)
        lensPaint.color = Color.parseColor("#FFD54F")
        canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconSize * 0.19f, lensPaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 23f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(
            "GPS Map Camera",
            iconRect.right + 12f * scale,
            badgeRect.centerY() + 8f * scale,
            badgeTextPaint
        )

        // 2. Left Satellite Map Thumbnail Box
        val mapWidth = if (stampData.showMiniRadar) 258f * scale else 0f
        val gap = if (stampData.showMiniRadar) 20f * scale else 0f

        if (stampData.showMiniRadar) {
            val mapRect = RectF(
                marginSide,
                cardTop,
                marginSide + mapWidth,
                cardBottom
            )
            drawSatelliteMapOnCanvas(canvas, mapRect, stampData, scale)
        }

        // 3. Right Dark Translucent Details Box
        val infoLeft = marginSide + mapWidth + gap
        val infoRight = w - marginSide
        val infoRect = RectF(infoLeft, cardTop, infoRight, cardBottom)

        val infoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 22, 22, 22)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(infoRect, 18f * scale, 18f * scale, infoBgPaint)

        val textStartX = infoLeft + 22f * scale
        val maxTextWidth = infoRight - textStartX - 18f * scale

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 37f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            setShadowLayer(3f * scale, 1f * scale, 1f * scale, Color.argb(160, 0, 0, 0))
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 25.5f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            setShadowLayer(2f * scale, 1f * scale, 1f * scale, Color.argb(150, 0, 0, 0))
        }

        // Line 1: Place Title ("Khawaja Wardag, Punjab, India")
        var cursorY = cardTop + 46f * scale
        canvas.drawText(
            ellipsizeText(stampData.placeTitle, titlePaint, maxTextWidth),
            textStartX,
            cursorY,
            titlePaint
        )

        // Line 2: Indian Flag Badge right below the title
        val flagTop = cursorY + 14f * scale
        val flagWidth = 46f * scale
        val flagHeight = 30f * scale
        drawIndianFlagOnCanvas(canvas, RectF(textStartX, flagTop, textStartX + flagWidth, flagTop + flagHeight), scale)

        // Line 3: Street Address ("32 32, Khawaja Wardag, Punjab 143605, India")
        cursorY = flagTop + flagHeight + 34f * scale
        canvas.drawText(
            ellipsizeText(stampData.addressLine, bodyPaint, maxTextWidth),
            textStartX,
            cursorY,
            bodyPaint
        )

        // Line 4: Coordinates ("Lat 31.956184° Long 75.082808°")
        cursorY += 35f * scale
        canvas.drawText(
            ellipsizeText(stampData.formatLatLongLine(), bodyPaint, maxTextWidth),
            textStartX,
            cursorY,
            bodyPaint
        )

        // Line 5: Date & Time ("Wednesday, 28/10/2026 02:05 PM GMT +05:30")
        cursorY += 35f * scale
        canvas.drawText(
            ellipsizeText(stampData.formatFullGpsMapDateTime(captureTimeMillis), bodyPaint, maxTextWidth),
            textStartX,
            cursorY,
            bodyPaint
        )

        // Line 6: Note ("Note : Captured by GPS Map Camera")
        cursorY += 35f * scale
        val noteText = "Note : " + stampData.customNote.ifBlank { "Captured by GPS Map Camera" }
        canvas.drawText(
            ellipsizeText(noteText, bodyPaint, maxTextWidth),
            textStartX,
            cursorY,
            bodyPaint
        )

        result
    }

    private suspend fun drawSatelliteMapOnCanvas(
        canvas: Canvas,
        rect: RectF,
        stampData: GpsStampData,
        scale: Float
    ) {
        val saveCount = canvas.save()
        val clipPath = Path().apply {
            addRoundRect(rect, 18f * scale, 18f * scale, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // 1. Earthy satellite terrain fallback
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.parseColor("#7D6B57"),
                    Color.parseColor("#5A6346"),
                    Color.parseColor("#8A755E"),
                    Color.parseColor("#4E5B3C")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(rect, basePaint)

        // 2. Draw real satellite tiles around (lat, lng)
        val zoom = 16
        val exactX = SatelliteTileCache.lonToTileX(stampData.longitude, zoom)
        val exactY = SatelliteTileCache.latToTileY(stampData.latitude, zoom)
        val centerX = floor(exactX).toInt()
        val centerY = floor(exactY).toInt()
        val fracX = (exactX - centerX).toFloat()
        val fracY = (exactY - centerY).toFloat()
        val tileDisplaySize = maxOf(rect.width(), rect.height()) * 1.15f
        val cx = rect.centerX()
        val cy = rect.centerY()
        val maxIdx = (1 shl zoom) - 1
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = (centerX + dx).coerceIn(0, maxIdx)
                val ty = (centerY + dy).coerceIn(0, maxIdx)
                // Use cached tile first for instant capture; only do a fast 800ms lookup if missing
                val key = SatelliteTileKey(zoom, tx, ty, isSatellite = true)
                val tileBmp = SatelliteTileCache.get(key)
                    ?: kotlinx.coroutines.withTimeoutOrNull(800L) {
                        SatelliteTileCache.fetchTile(zoom, tx, ty, isSatellite = true)
                    }
                if (tileBmp != null) {
                    val left = (cx + (dx - fracX) * tileDisplaySize).roundToInt()
                    val top = (cy + (dy - fracY) * tileDisplaySize).roundToInt()
                    val dstRect = Rect(
                        left,
                        top,
                        (left + tileDisplaySize).roundToInt(),
                        (top + tileDisplaySize).roundToInt()
                    )
                    canvas.drawBitmap(tileBmp, null, dstRect, bmpPaint)
                }
            }
        }

        // 3. Blue directional cone beam extending down-right from center pin
        val conePath = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + rect.width() * 0.28f, cy + rect.height() * 0.26f)
            lineTo(cx + rect.width() * 0.05f, cy + rect.height() * 0.36f)
            close()
        }
        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, rect.width() * 0.4f,
                Color.argb(175, 41, 121, 255),
                Color.argb(30, 41, 121, 255),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawPath(conePath, conePaint)

        // 4. Red Google Teardrop Pin at Center
        val pinHeadY = cy - 28f * scale
        val pinRadius = 15f * scale
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA4335")
            style = Paint.Style.FILL
        }
        val pinTriangle = Path().apply {
            moveTo(cx - pinRadius * 0.85f, pinHeadY + 5f * scale)
            lineTo(cx + pinRadius * 0.85f, pinHeadY + 5f * scale)
            lineTo(cx, cy + 3f * scale)
            close()
        }
        canvas.drawPath(pinTriangle, pinPaint)
        canvas.drawCircle(cx, pinHeadY, pinRadius, pinPaint)

        val pinInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E1B13")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, pinHeadY, 6f * scale, pinInnerPaint)

        // 5. "Google" watermark text at bottom-left of satellite map
        val googlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(6f * scale, 1.5f * scale, 2f * scale, Color.argb(220, 0, 0, 0))
        }
        canvas.drawText(
            "Google",
            rect.left + 16f * scale,
            rect.bottom - 16f * scale,
            googlePaint
        )

        canvas.restoreToCount(saveCount)
    }

    private fun drawIndianFlagOnCanvas(canvas: Canvas, rect: RectF, scale: Float) {
        val save = canvas.save()
        val path = Path().apply { addRoundRect(rect, 4f * scale, 4f * scale, Path.Direction.CW) }
        canvas.clipPath(path)

        val stripeH = rect.height() / 3f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#FF9933")
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + stripeH, p)
        p.color = Color.WHITE
        canvas.drawRect(rect.left, rect.top + stripeH, rect.right, rect.top + stripeH * 2f, p)
        p.color = Color.parseColor("#138808")
        canvas.drawRect(rect.left, rect.top + stripeH * 2f, rect.right, rect.bottom, p)

        p.color = Color.parseColor("#000080")
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * scale
        canvas.drawCircle(rect.centerX(), rect.centerY(), stripeH * 0.36f, p)

        canvas.restoreToCount(save)
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
    }

    /**
     * Immediately saves the GPS-stamped photo onto the user's device in `DCIM/Camera` (and MediaStore)
     * and triggers MediaScannerConnection so it appears in the phone's main Camera Roll / Gallery
     * the exact moment the shutter is clicked.
     */
    suspend fun saveStampedBitmapToGallery(
        context: Context,
        stampedBitmap: Bitmap,
        stampData: GpsStampData,
        captureTimeMillis: Long
    ): CapturedPhotoItem? = withContext(Dispatchers.IO) {
        val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        val fileName = "GPS_IMG_${timestampFormat.format(Date(captureTimeMillis))}.jpg"
        val summary = "${stampData.placeTitle} • ${stampData.formatFullGpsMapDateTime(captureTimeMillis)}"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.TITLE, fileName.removeSuffix(".jpg"))
            put(MediaStore.Images.Media.DESCRIPTION, summary)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, captureTimeMillis)
            put(MediaStore.Images.Media.DATE_ADDED, captureTimeMillis / 1000L)
            put(MediaStore.Images.Media.DATE_MODIFIED, captureTimeMillis / 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DCIM}/Camera"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        var insertedUri: Uri? = null
        try {
            insertedUri = resolver.insert(collectionUri, contentValues)
            if (insertedUri != null) {
                val outStream: OutputStream? = resolver.openOutputStream(insertedUri)
                if (outStream != null) {
                    outStream.use { stream ->
                        stampedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                        stream.flush()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val completeValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        resolver.update(insertedUri, completeValues, null, null)
                    }

                    // Notify system MediaScanner & Gallery apps immediately
                    try {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/Camera/$fileName"),
                            arrayOf("image/jpeg"),
                            null
                        )
                    } catch (_: Exception) {
                    }

                    return@withContext CapturedPhotoItem(
                        id = System.currentTimeMillis(),
                        uri = insertedUri,
                        displayName = fileName,
                        dateTakenMillis = captureTimeMillis,
                        stampSummary = summary
                    )
                } else {
                    resolver.delete(insertedUri, null, null)
                }
            }
        } catch (_: Exception) {
            if (insertedUri != null) {
                try {
                    resolver.delete(insertedUri, null, null)
                } catch (_: Exception) {
                }
            }
        }

        // Guaranteed fallback to device storage directory if MediaStore volume is busy
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, fileName)
            java.io.FileOutputStream(file).use { stream ->
                stampedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                stream.flush()
            }
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            CapturedPhotoItem(
                id = System.currentTimeMillis(),
                uri = Uri.fromFile(file),
                displayName = fileName,
                dateTakenMillis = captureTimeMillis,
                stampSummary = summary
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadSavedPhotosFromGallery(context: Context): List<CapturedPhotoItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<CapturedPhotoItem>()
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED
                )
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("GPS_IMG_%")
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                    val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                    while (cursor.moveToNext() && items.size < 30) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "GPS_Photo.jpg"
                        val dateTaken = cursor.getLong(dateTakenCol).let {
                            if (it > 0L) it else cursor.getLong(dateAddedCol) * 1000L
                        }
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a", Locale.ENGLISH)
                        items.add(
                            CapturedPhotoItem(
                                id = id,
                                uri = contentUri,
                                displayName = name,
                                dateTakenMillis = dateTaken,
                                stampSummary = sdf.format(Date(dateTaken))
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
            items
        }
}
