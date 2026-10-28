package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalEmerald
import com.example.ui.theme.TacticalNavyDark
import com.example.ui.theme.TacticalNavySurface
import com.example.util.SatelliteTileCache
import com.example.util.SatelliteTileKey
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun DraggableMapPreviewOverlay(
    stampData: GpsStampData,
    onRefreshGps: () -> Unit,
    onNormalizedOffsetChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var zoomLevel by remember { mutableIntStateOf(16) }
    var isExpandedSize by remember { mutableStateOf(false) }

    val cardWidthDp = if (isExpandedSize) 186.dp else 142.dp
    val cardHeightDp = if (isExpandedSize) 186.dp else 146.dp

    val loadedTiles = remember { mutableStateMapOf<SatelliteTileKey, Bitmap>() }
    val exactTileX = remember(stampData.latitude, stampData.longitude, zoomLevel) {
        SatelliteTileCache.lonToTileX(stampData.longitude, zoomLevel)
    }
    val exactTileY = remember(stampData.latitude, stampData.longitude, zoomLevel) {
        SatelliteTileCache.latToTileY(stampData.latitude, zoomLevel)
    }
    val centerTileX = floor(exactTileX).toInt()
    val centerTileY = floor(exactTileY).toInt()

    LaunchedEffect(centerTileX, centerTileY, zoomLevel) {
        val maxIndex = (1 shl zoomLevel) - 1
        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = (centerTileX + dx).coerceIn(0, maxIndex)
                val ty = (centerTileY + dy).coerceIn(0, maxIndex)
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

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val cardWidthPx = with(density) { cardWidthDp.toPx() }
        val cardHeightPx = with(density) { cardHeightDp.toPx() }

        val minX = with(density) { 12.dp.toPx() }
        val maxX = (containerWidthPx - cardWidthPx - minX).coerceAtLeast(minX)
        val minY = with(density) { 124.dp.toPx() }
        val maxY = (containerHeightPx - cardHeightPx - with(density) { 250.dp.toPx() }).coerceAtLeast(minY)

        var offsetX by remember(containerWidthPx) {
            mutableFloatStateOf((maxX - with(density) { 4.dp.toPx() }).coerceIn(minX, maxX))
        }
        var offsetY by remember(containerHeightPx) {
            mutableFloatStateOf((minY + with(density) { 6.dp.toPx() }).coerceIn(minY, maxY))
        }

        val clampedX = offsetX.coerceIn(minX, maxX)
        val clampedY = offsetY.coerceIn(minY, maxY)

        LaunchedEffect(clampedX, clampedY, containerWidthPx, containerHeightPx) {
            if (containerWidthPx > 0f && containerHeightPx > 0f) {
                onNormalizedOffsetChanged(
                    clampedX / containerWidthPx,
                    clampedY / containerHeightPx
                )
            }
        }

        Surface(
            color = Color(0xDD181818),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                .size(width = cardWidthDp, height = cardHeightDp)
                .shadow(10.dp, RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .pointerInput(minX, maxX, minY, maxY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(minX, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxY)
                    }
                }
                .testTag("draggable_map_preview_overlay")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Compact Top Drag Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalNavySurface.copy(alpha = 0.92f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Drag satellite map",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (stampData.hasGpsFix) TacticalEmerald else TacticalAmber)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SATELLITE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = if (isExpandedSize) "–" else "⤢",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                        color = TacticalCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isExpandedSize = !isExpandedSize }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .testTag("map_toggle_size_button")
                    )
                }

                // Satellite Map Canvas with Red Pin, Blue Directional Cone & "Google" Watermark
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("draggable_map_canvas")
                    ) {
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val cy = h / 2f

                        // 1. Satellite terrain base fallback
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
                        val patchColors = listOf(
                            Color(0xFF394F2E),
                            Color(0xFF9C8770),
                            Color(0xFF6B5B4B),
                            Color(0xFF2D4224),
                            Color(0xFFA8947D)
                        )
                        for (r in 0..4) {
                            for (c in 0..4) {
                                val px = c * w / 4f
                                val py = r * h / 4f
                                val col = patchColors[(r * 3 + c * 2) % patchColors.size]
                                drawRect(
                                    color = col.copy(alpha = 0.55f),
                                    topLeft = Offset(px, py),
                                    size = Size(w / 3.8f, h / 3.8f)
                                )
                            }
                        }

                        // 2. Real Satellite Tiles
                        val tileDisplaySize = maxOf(w, h) * 1.15f
                        val fracX = (exactTileX - centerTileX).toFloat()
                        val fracY = (exactTileY - centerTileY).toFloat()
                        val maxTileIdx = (1 shl zoomLevel) - 1

                        for (dx in -1..1) {
                            for (dy in -1..1) {
                                val tx = (centerTileX + dx).coerceIn(0, maxTileIdx)
                                val ty = (centerTileY + dy).coerceIn(0, maxTileIdx)
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

                        // 3. Blue Directional Cone Beam extending down-right from pin
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

                        // 4. Red Teardrop Google Map Pin
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
                        drawCircle(
                            color = Color(0xFF8E1B13),
                            radius = 3.8f,
                            center = Offset(cx, pinHeadY)
                        )
                    }

                    // Bottom-left "Google" logo watermark matching screenshot
                    Text(
                        text = "Google",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.85f),
                                offset = Offset(1f, 1.5f),
                                blurRadius = 4f
                            )
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 7.dp, bottom = 4.dp)
                            .testTag("draggable_map_coordinates_text")
                    )

                    // Right-side Zoom (+ / -) & Recenter GPS Controls
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 5.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(TacticalNavyDark.copy(alpha = 0.78f))
                                .border(0.7.dp, TacticalBorder, RoundedCornerShape(5.dp))
                                .clickable { zoomLevel = (zoomLevel + 1).coerceAtMost(18) }
                                .testTag("map_zoom_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(TacticalNavyDark.copy(alpha = 0.78f))
                                .border(0.7.dp, TacticalBorder, RoundedCornerShape(5.dp))
                                .clickable { zoomLevel = (zoomLevel - 1).coerceAtLeast(3) }
                                .testTag("map_zoom_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(TacticalNavyDark.copy(alpha = 0.78f))
                                .border(0.7.dp, TacticalAmber.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                                .clickable { onRefreshGps() }
                                .testTag("map_recenter_gps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Center Live GPS",
                                tint = TacticalAmber,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
