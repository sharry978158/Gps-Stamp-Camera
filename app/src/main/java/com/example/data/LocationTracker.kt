package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class LiveLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val placeTitle: String,
    val addressLine: String
)

data class PlaceSearchResult(
    val placeTitle: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double
)

class LocationTracker(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(scope: CoroutineScope): Flow<LiveLocationSnapshot> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        // First try lastLocation for immediate display of the device's real GPS
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    scope.launch {
                        val (title, addr) = reverseGeocode(loc.latitude, loc.longitude)
                        trySend(
                            LiveLocationSnapshot(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                altitude = loc.altitude,
                                accuracy = loc.accuracy,
                                placeTitle = title,
                                addressLine = addr
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                scope.launch {
                    val (title, addr) = reverseGeocode(loc.latitude, loc.longitude)
                    trySend(
                        LiveLocationSnapshot(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitude = loc.altitude,
                            accuracy = loc.accuracy,
                            placeTitle = title,
                            addressLine = addr
                        )
                    )
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            close()
        } catch (_: Exception) {
            close()
        }

        awaitClose {
            try {
                fusedClient.removeLocationUpdates(callback)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchSingleFix(): LiveLocationSnapshot? = withContext(Dispatchers.IO) {
        try {
            val location = suspendCancellableCoroutine<Location?> { cont ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            } ?: suspendCancellableCoroutine<Location?> { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            } ?: return@withContext null

            val (title, address) = reverseGeocode(location.latitude, location.longitude)
            LiveLocationSnapshot(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy,
                placeTitle = title,
                addressLine = address
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves (lat, lng) into (placeTitle, fullAddressLine) using Android Geocoder first,
     * and falls back to OpenStreetMap Nominatim reverse geocoding so pin-point map picks
     * always display real village/city/state/country names.
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): Pair<String, String> = withContext(Dispatchers.IO) {
        // 1. Try Android native Geocoder
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.ENGLISH)
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(result: MutableList<Address>) {
                                if (cont.isActive) cont.resume(result)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)
                }

                val first = addresses?.firstOrNull()
                if (first != null) {
                    val cityOrVillage = first.locality ?: first.subLocality ?: first.subAdminArea ?: first.featureName
                    val titleParts = listOfNotNull(
                        cityOrVillage,
                        first.adminArea,
                        first.countryName
                    ).filter { it.isNotBlank() }.distinct()

                    val placeTitle = if (titleParts.isNotEmpty()) {
                        titleParts.joinToString(", ")
                    } else {
                        String.format(Locale.US, "Lat %.5f°, Long %.5f°", lat, lng)
                    }

                    val fullLine = first.getAddressLine(0)
                    val detailAddress = if (!fullLine.isNullOrBlank()) {
                        fullLine
                    } else {
                        placeTitle
                    }
                    return@withContext placeTitle to detailAddress
                }
            }
        } catch (_: Exception) {
        }

        // 2. Fallback to Nominatim Reverse Geocoding over HTTPS
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng&accept-language=en")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "GPSMapCameraAndroidApp/2.0")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val displayName = json.optString("display_name", "")
                val addrObj = json.optJSONObject("address")
                if (addrObj != null) {
                    val place = addrObj.optString("village")
                        .ifBlank { addrObj.optString("town") }
                        .ifBlank { addrObj.optString("city") }
                        .ifBlank { addrObj.optString("suburb") }
                        .ifBlank { addrObj.optString("county") }
                    val state = addrObj.optString("state")
                    val country = addrObj.optString("country")
                    val titleParts = listOf(place, state, country).filter { it.isNotBlank() }
                    val title = if (titleParts.isNotEmpty()) {
                        titleParts.joinToString(", ")
                    } else {
                        displayName.take(45)
                    }
                    val full = displayName.ifBlank { title }
                    if (title.isNotBlank()) {
                        return@withContext title to full
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }

        val coordTitle = String.format(Locale.US, "GPS %.5f° N, %.5f° E", lat, lng)
        val coordAddr = String.format(Locale.US, "Lat %.6f° Long %.6f°", lat, lng)
        coordTitle to coordAddr
    }

    /**
     * Searches villages, towns, cities, or addresses by name so the user can quickly jump
     * to any pin-point location on the full-screen map picker.
     */
    suspend fun searchPlacesByName(query: String): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val results = mutableListOf<PlaceSearchResult>()

        // 1. Try Android Geocoder first
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.ENGLISH)
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(trimmed, 5)
                addresses?.forEach { addr ->
                    if (addr.hasLatitude() && addr.hasLongitude()) {
                        val locality = addr.locality ?: addr.subLocality ?: addr.subAdminArea ?: addr.featureName
                        val titleParts = listOfNotNull(locality, addr.adminArea, addr.countryName)
                            .filter { it.isNotBlank() }
                            .distinct()
                        val title = if (titleParts.isNotEmpty()) titleParts.joinToString(", ") else trimmed
                        val full = addr.getAddressLine(0) ?: title
                        results.add(
                            PlaceSearchResult(
                                placeTitle = title,
                                fullAddress = full,
                                latitude = addr.latitude,
                                longitude = addr.longitude
                            )
                        )
                    }
                }
                if (results.isNotEmpty()) return@withContext results
            }
        } catch (_: Exception) {
        }

        // 2. Fallback to Nominatim Search API
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2&addressdetails=1&limit=5&accept-language=en")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "GPSMapCameraAndroidApp/2.0")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(responseText)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val lat = item.optString("lat").toDoubleOrNull() ?: continue
                    val lon = item.optString("lon").toDoubleOrNull() ?: continue
                    val displayName = item.optString("display_name", trimmed)
                    val name = item.optString("name", "")
                    val addr = item.optJSONObject("address")
                    val state = addr?.optString("state").orEmpty()
                    val country = addr?.optString("country").orEmpty()
                    val titleParts = listOf(name, state, country).filter { it.isNotBlank() }.distinct()
                    val title = if (titleParts.isNotEmpty()) titleParts.joinToString(", ") else displayName
                    results.add(
                        PlaceSearchResult(
                            placeTitle = title,
                            fullAddress = displayName,
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }

        results
    }
}
