package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GpsStampData
import com.example.util.SatelliteTileCache
import com.example.util.SatelliteTileKey
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Exact 1:1 visual replica of the "GPS Map Camera" bottom overlay from the user's screenshot:
 * - Top-right floating dark pill badge `[📷 GPS Map Camera]`
 * - Left: Square rounded Satellite Map thumbnail with red teardrop pin, blue directional beam, and "Google" watermark at bottom-left
 * - Right: Dark translucent rounded card with:
 *   1. Large white title ("Khawaja Wardag, Punjab, India")
 *   2. Country flag ("🇮🇳") on its own row
 *   3. Full street address ("32 32, Khawaja Wardag, Punjab 143605, India")
 *   4. Coordinates ("Lat 31.956184° Long 75.082808°")
 *   5. Day, Date, Time, GMT offset ("Wednesday, 28/10/2026 02:05 PM GMT +05:30")
 *   6. Note ("Note : Captured by GPS Map Camera")
 */
@Composable
fun LiveGpsStampCard(
    stampData: GpsStampData,
    currentLiveMillis: Long,
    onEditClick: () -> Unit,
    onMapClick: () -> Unit = onEditClick,
    modifier: Modifier = Modifier
) {
    val zoomLevel = 16
    val loadedTiles = remember { mutableStateMapOf<SatelliteTileKey, Bitmap>() }

    val exactTileX = remember(stampData.latitude, stampData.longitude) {
        SatelliteTileCache.lonToTileX(stampData.longitude, zoomLevel)
    }
    val exactTileY = remember(stampData.latitude, stampData.longitude) {
        SatelliteTileCache.latToTileY(stampData.latitude, zoomLevel)
    }
    val centerTileX = floor(exactTileX).toInt()
    val centerTileY = floor(exactTileY).toInt()

    LaunchedEffect(centerTileX, centerTileY) {
        val maxIdx = (1 shl zoomLevel) - 1
        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = (centerTileX + dx).coerceIn(0, maxIdx)
                val ty = (centerTileY + dy).coerceIn(0, maxIdx)
                val key = SatelliteTileKey(zoomLevel, tx, ty, isSatellite = true)
                val cached = SatelliteTileCache.get(key)
                if (cached != null) {
                    loadedTiles[key] = cached
                } else {
                    val bmp = SatelliteTileCache.fetchTile(zoomLevel, tx, ty, isSatellite = true)
                    if (bmp != null) {
                        loadedTiles[key] = bmp
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick)
            .testTag("live_gps_stamp_card")
    ) {
        // Top-right "GPS Map Camera" watermark badge attached right above the main stamp card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 2.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = Color(0xBB1C1C1C),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                ) {
                    // Mini cyan/yellow camera lens badge icon matching screenshot
                    Canvas(modifier = Modifier.size(14.dp)) {
                        drawRoundRect(
                            color = Color(0xFF29B6F6),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                        drawCircle(
                            color = Color(0xFF1B3A4B),
                            radius = size.minDimension * 0.36f
                        )
                        drawCircle(
                            color = Color(0xFFFFD54F),
                            radius = size.minDimension * 0.20f
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "GPS Map Camera",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Bottom Row: Left Square Satellite Map + Right Dark Translucent Details Box
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            // LEFT: Square Rounded Satellite Map Thumbnail with Red Pin, Blue Beam & "Google" text
            // Tapping the map thumbnail directly opens the full-screen Google Map Pin-Point Picker!
            if (stampData.showMiniRadar) {
                SatelliteMapSquareBox(
                    exactTileX = exactTileX,
                    exactTileY = exactTileY,
                    centerTileX = centerTileX,
                    centerTileY = centerTileY,
                    zoomLevel = zoomLevel,
                    loadedTiles = loadedTiles,
                    modifier = Modifier
                        .size(width = 112.dp, height = 132.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onMapClick)
                        .testTag("stamp_satellite_map_box")
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // RIGHT: Dark Translucent Rounded Info Box matching screenshot
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(132.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xB8181818))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Place Title: "Khawaja Wardag, Punjab, India"
                    Text(
                        text = stampData.placeTitle,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.5.sp,
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.6f),
                                offset = Offset(1f, 1f),
                                blurRadius = 2f
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("stamp_place_title_text")
                    )

                    // 2. India Flag Icon (matching the flag right under the title in the screenshot)
                    IndianFlagBadge(
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .size(width = 20.dp, height = 13.dp)
                    )

                    // 3. Full Street Address: "32 32, Khawaja Wardag, Punjab 143605, India"
                    Text(
                        text = stampData.addressLine,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.2.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("stamp_address_text")
                    )

                    // 4. Lat / Long Line: "Lat 31.956184° Long 75.082808°"
                    Text(
                        text = stampData.formatLatLongLine(),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.2.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("stamp_coordinates_text")
                    )

                    // 5. Full Date, Time & GMT Offset: "Wednesday, 28/10/2026 02:05 PM GMT +05:30"
                    Text(
                        text = stampData.formatFullGpsMapDateTime(currentLiveMillis),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.2.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("stamp_datetime_text")
                    )

                    // 6. Note Line: "Note : Captured by GPS Map Camera"
                    val noteDisplay = if (stampData.customNote.isNotBlank()) {
                        stampData.customNote
                    } else {
                        "Captured by GPS Map Camera"
                    }
                    Text(
                        text = "Note : $noteDisplay",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.2.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("stamp_note_text")
                    )
                }
            }
        }
    }
}

@Composable
fun SatelliteMapSquareBox(
    exactTileX: Double,
    exactTileY: Double,
    centerTileX: Int,
    centerTileY: Int,
    zoomLevel: Int,
    loadedTiles: Map<SatelliteTileKey, Bitmap>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // 1. Realistic Aerial Satellite Terrain Fallback Base (earthy fields, trees, village blocks)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF7D6B57),
                        Color(0xFF5A6346),
                        Color(0xFF8A755E),
                        Color(0xFF4E5B3C)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
            )

            // Draw realistic aerial satellite patches/trees/buildings when offline or before tiles arrive
            val patchColors = listOf(
                Color(0xFF394F2E),
                Color(0xFF9C8770),
                Color(0xFF6B5B4B),
                Color(0xFF2D4224),
                Color(0xFFA8947D)
            )
            for (r in 0..5) {
                for (c in 0..5) {
                    val px = (c * w / 5f)
                    val py = (r * h / 5f)
                    val color = patchColors[(r * 3 + c * 2) % patchColors.size]
                    drawRect(
                        color = color.copy(alpha = 0.55f),
                        topLeft = Offset(px, py),
                        size = Size(w / 4.5f, h / 4.5f)
                    )
                }
            }
            // Dark green foliage clusters
            drawCircle(Color(0xFF263B1D), radius = w * 0.12f, center = Offset(w * 0.25f, h * 0.28f))
            drawCircle(Color(0xFF2D4422), radius = w * 0.09f, center = Offset(w * 0.72f, h * 0.34f))
            drawCircle(Color(0xFF23361B), radius = w * 0.14f, center = Offset(w * 0.68f, h * 0.74f))
            drawCircle(Color(0xFF314926), radius = w * 0.10f, center = Offset(w * 0.22f, h * 0.68f))

            // 2. Draw Real Aerial Satellite Tiles if loaded
            val tileDisplaySize = maxOf(w, h) * 1.15f
            val fracX = (exactTileX - centerTileX).toFloat()
            val fracY = (exactTileY - centerTileY).toFloat()
            val maxIdx = (1 shl zoomLevel) - 1

            for (dx in -1..1) {
                for (dy in -1..1) {
                    val tx = (centerTileX + dx).coerceIn(0, maxIdx)
                    val ty = (centerTileY + dy).coerceIn(0, maxIdx)
                    val bmp = loadedTiles[SatelliteTileKey(zoomLevel, tx, ty, isSatellite = true)]
                    if (bmp != null) {
                        val left = cx + (dx - fracX) * tileDisplaySize
                        val top = cy + (dy - fracY) * tileDisplaySize
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(tileDisplaySize.roundToInt(), tileDisplaySize.roundToInt())
                        )
                    }
                }
            }

            // 3. Blue Directional Cone Beam extending down-right from center pin (matching screenshot!)
            val conePath = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + w * 0.28f, cy + h * 0.26f)
                lineTo(cx + w * 0.05f, cy + h * 0.36f)
                close()
            }
            drawPath(
                path = conePath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xAA2979FF),
                        Color(0x222979FF)
                    ),
                    center = Offset(cx, cy),
                    radius = w * 0.4f
                )
            )

            // 4. Red Teardrop Google Map Pin at Center
            val pinTipY = cy
            val pinHeadY = cy - 18f
            val pinHeadRadius = 9.5f

            val pinPath = Path().apply {
                moveTo(cx - pinHeadRadius * 0.85f, pinHeadY + 4f)
                lineTo(cx + pinHeadRadius * 0.85f, pinHeadY + 4f)
                lineTo(cx, pinTipY + 2f)
                close()
            }
            drawPath(pinPath, color = Color(0xFFEA4335))
            drawCircle(
                color = Color(0xFFEA4335),
                radius = pinHeadRadius,
                center = Offset(cx, pinHeadY)
            )
            // Dark maroon inner dot inside red pin
            drawCircle(
                color = Color(0xFF8E1B13),
                radius = 3.8f,
                center = Offset(cx, pinHeadY)
            )
        }

        // Bottom-left "Google" white logo watermark with subtle dark outline (exact match to screenshot)
        Text(
            text = "Google",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    offset = Offset(1f, 1.5f),
                    blurRadius = 4f
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 5.dp)
        )
    }
}

@Composable
private fun IndianFlagBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(2.dp))) {
        val w = size.width
        val h = size.height
        val stripeH = h / 3f
        // Saffron top
        drawRect(color = Color(0xFFFF9933), topLeft = Offset(0f, 0f), size = Size(w, stripeH))
        // White middle
        drawRect(color = Color.White, topLeft = Offset(0f, stripeH), size = Size(w, stripeH))
        // Green bottom
        drawRect(color = Color(0xFF138808), topLeft = Offset(0f, stripeH * 2f), size = Size(w, stripeH))
        // Navy Ashoka Chakra in center
        drawCircle(
            color = Color(0xFF000080),
            radius = stripeH * 0.38f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(width = 1.2f)
        )
    }
}
