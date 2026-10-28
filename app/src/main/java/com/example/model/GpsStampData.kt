package com.example.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class StampTemplate(val labelPunjabi: String, val labelEnglish: String) {
    CLASSIC("GPS Map Camera (Satellite)", "GPS Map Camera"),
    TACTICAL("Tactical HUD", "Tactical HUD"),
    MINIMAL("Minimal Bar", "Minimal Bar")
}

enum class CoordinateFormat(val label: String) {
    DECIMAL("Decimal (Lat 31.956184°)"),
    DMS("DMS (31°57'22.3\" N)")
}

data class GpsStampData(
    val latitude: Double = 31.956184,
    val longitude: Double = 75.082808,
    val altitudeMeters: Double = 248.0,
    val accuracyMeters: Float = 3.8f,
    val placeTitle: String = "Khawaja Wardag, Punjab, India",
    val addressLine: String = "32 32, Khawaja Wardag, Punjab 143605, India",
    val isLiveGps: Boolean = false,
    val hasGpsFix: Boolean = false,
    val isCustomLocation: Boolean = false,
    val customTimestampMillis: Long? = null,
    val customNote: String = "Captured by GPS Map Camera",
    val template: StampTemplate = StampTemplate.CLASSIC,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val showAltitudeAndAccuracy: Boolean = false,
    val showMiniRadar: Boolean = true
) {
    fun getDisplayTimestampMillis(currentLiveMillis: Long): Long {
        return customTimestampMillis ?: currentLiveMillis
    }

    /**
     * Exact GPS Map Camera date-time line format:
     * "Wednesday, 28/10/2026 02:05 PM GMT +05:30"
     */
    fun formatFullGpsMapDateTime(currentLiveMillis: Long): String {
        val millis = getDisplayTimestampMillis(currentLiveMillis)
        val date = Date(millis)
        val dayFormat = SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a", Locale.ENGLISH)
        val gmtOffset = formatGmtOffset(millis)
        return "${dayFormat.format(date)} $gmtOffset"
    }

    private fun formatGmtOffset(timeMillis: Long): String {
        val tz = TimeZone.getDefault()
        val offsetMillis = tz.getOffset(timeMillis)
        val sign = if (offsetMillis >= 0) "+" else "-"
        val totalMinutes = abs(offsetMillis) / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format(Locale.US, "GMT %s%02d:%02d", sign, hours, minutes)
    }

    /**
     * Exact GPS Map Camera coordinate line format:
     * "Lat 31.956184° Long 75.082808°"
     */
    fun formatLatLongLine(): String {
        return when (coordinateFormat) {
            CoordinateFormat.DECIMAL -> {
                String.format(Locale.US, "Lat %.6f° Long %.6f°", latitude, longitude)
            }
            CoordinateFormat.DMS -> {
                "Lat ${toDms(latitude, isLat = true)} Long ${toDms(longitude, isLat = false)}"
            }
        }
    }

    fun formatDate(currentLiveMillis: Long): String {
        val millis = getDisplayTimestampMillis(currentLiveMillis)
        val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.ENGLISH)
        return sdf.format(Date(millis))
    }

    fun formatTime(currentLiveMillis: Long): String {
        val millis = getDisplayTimestampMillis(currentLiveMillis)
        val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        return "${sdf.format(Date(millis))} ${formatGmtOffset(millis)}"
    }

    fun formatDateShort(currentLiveMillis: Long): String {
        val millis = getDisplayTimestampMillis(currentLiveMillis)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        return sdf.format(Date(millis))
    }

    fun formatLat(): String {
        return when (coordinateFormat) {
            CoordinateFormat.DECIMAL -> String.format(Locale.US, "%.6f°", latitude)
            CoordinateFormat.DMS -> toDms(latitude, isLat = true)
        }
    }

    fun formatLng(): String {
        return when (coordinateFormat) {
            CoordinateFormat.DECIMAL -> String.format(Locale.US, "%.6f°", longitude)
            CoordinateFormat.DMS -> toDms(longitude, isLat = false)
        }
    }

    private fun toDms(coord: Double, isLat: Boolean): String {
        val dir = if (isLat) {
            if (coord >= 0) "N" else "S"
        } else {
            if (coord >= 0) "E" else "W"
        }
        val absCoord = abs(coord)
        val degrees = absCoord.toInt()
        val minutesFull = (absCoord - degrees) * 60.0
        val minutes = minutesFull.toInt()
        val seconds = (minutesFull - minutes) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\" %s", degrees, minutes, seconds, dir)
    }
}

data class CapturedPhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTakenMillis: Long,
    val stampSummary: String
)
