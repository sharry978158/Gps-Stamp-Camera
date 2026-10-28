package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.PlaceSearchResult
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberLight
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalEmerald
import com.example.ui.theme.TacticalNavyCard
import com.example.ui.theme.TacticalNavyDark
import com.example.ui.theme.TacticalNavySurface
import java.util.Locale

/**
 * Full-Screen Interactive Google Hybrid/Satellite Pin-Point Map Picker.
 * Users can:
 * - Pan, pinch-zoom, or tap anywhere on the interactive Google Satellite/Hybrid/Street Map to drop a pin-point marker
 * - Search any village, city, or landmark by name
 * - Tap "ਕਰੰਟ ਲੋਕੇਸ਼ਨ (Current GPS)" to immediately jump to their device's live GPS coordinates
 * - Open in the Google Maps app if desired
 * - Confirm the selected pin-point location to update the camera stamp
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullScreenMapPickerDialog(
    initialLat: Double,
    initialLng: Double,
    initialPlaceTitle: String,
    initialAddress: String,
    isResolvingAddress: Boolean,
    searchResults: List<PlaceSearchResult>,
    isSearchingPlaces: Boolean,
    onSearchQuery: (String) -> Unit,
    onClearSearchResults: () -> Unit,
    onMapPinMoved: (Double, Double) -> Unit,
    onUseCurrentDeviceGps: ((Double, Double) -> Unit) -> Unit,
    onConfirmLocation: (lat: Double, lng: Double, placeTitle: String, addressLine: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var pickedLat by remember { mutableDoubleStateOf(initialLat) }
    var pickedLng by remember { mutableDoubleStateOf(initialLng) }
    var searchQuery by remember { mutableStateOf("") }
    var isSatelliteHybrid by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        onDismiss()
    }

    val mapHtml = remember {
        buildInteractiveSatelliteMapHtml(initialLat, initialLng)
    }

    Surface(
        color = TacticalNavyDark,
        modifier = Modifier
            .fillMaxSize()
            .testTag("full_screen_map_picker")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Top Header Bar + Search Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalNavySurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("map_picker_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = "Pin-Point Location on Google Map",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Tap anywhere on the map or drag the red pin",
                                style = MaterialTheme.typography.labelMedium,
                                color = TacticalAmberLight
                            )
                        }
                    }

                    // Button to open in external Google Maps app if user wants
                    OutlinedButton(
                        onClick = {
                            try {
                                val gmmIntentUri = Uri.parse("geo:$pickedLat,$pickedLng?q=$pickedLat,$pickedLng")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                if (mapIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(mapIntent)
                                } else {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://www.google.com/maps/search/?api=1&query=$pickedLat,$pickedLng")
                                        )
                                    )
                                }
                            } catch (_: Exception) {
                            }
                        },
                        border = BorderStroke(1.dp, TacticalCyan),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Google Maps",
                            style = MaterialTheme.typography.labelMedium,
                            color = TacticalCyan
                        )
                    }
                }

                // Village / City / Address Search Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search village, city, or address…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA2B9CE)
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TacticalCyan
                            )
                        },
                        trailingIcon = {
                            if (isSearchingPlaces) {
                                CircularProgressIndicator(
                                    color = TacticalCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else if (searchQuery.isNotBlank()) {
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        onSearchQuery(searchQuery)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TacticalCyan,
                                        contentColor = Color(0xFF00262C)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .height(32.dp)
                                        .testTag("map_search_submit_button")
                                ) {
                                    Text("Search", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                onSearchQuery(searchQuery)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TacticalCyan,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_input")
                    )
                }

                // Dropdown list of search results if available
                if (searchResults.isNotEmpty()) {
                    Surface(
                        color = TacticalNavyCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TacticalCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier.height(150.dp)
                        ) {
                            items(searchResults) { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pickedLat = item.latitude
                                            pickedLng = item.longitude
                                            onClearSearchResults()
                                            onMapPinMoved(item.latitude, item.longitude)
                                            webViewRef?.evaluateJavascript(
                                                "window.moveMapTo(${item.latitude}, ${item.longitude});",
                                                null
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = TacticalAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.placeTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.fullAddress,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFFA2B9CE),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Full-Screen Interactive Google Satellite / Hybrid Map Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false
                            }
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onPinPicked(lat: Double, lng: Double) {
                                        post {
                                            pickedLat = lat
                                            pickedLng = lng
                                            onMapPinMoved(lat, lng)
                                        }
                                    }
                                },
                                "AndroidMapBridge"
                            )
                            loadDataWithBaseURL(
                                "https://maps.google.com/",
                                mapHtml,
                                "text/html",
                                "UTF-8",
                                null
                            )
                            webViewRef = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("interactive_google_map_webview")
                )

                // Floating Right Controls: Switch Map Layer (Satellite Hybrid vs Street) & Current GPS Button
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                ) {
                    // Toggle Satellite / Street Map Layer
                    Surface(
                        color = TacticalNavyDark.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TacticalCyan),
                        modifier = Modifier
                            .clickable {
                                isSatelliteHybrid = !isSatelliteHybrid
                                val mode = if (isSatelliteHybrid) "hybrid" else "roadmap"
                                webViewRef?.evaluateJavascript(
                                    "window.setMapLayer('$mode');",
                                    null
                                )
                            }
                            .testTag("toggle_map_layer_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Toggle Satellite Layer",
                                tint = TacticalCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSatelliteHybrid) "Satellite View" else "Street Map",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }

                    // Get Current Device GPS Location Button
                    Surface(
                        color = TacticalEmerald,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clickable {
                                onUseCurrentDeviceGps { gpsLat, gpsLng ->
                                    pickedLat = gpsLat
                                    pickedLng = gpsLng
                                    webViewRef?.evaluateJavascript(
                                        "window.moveMapTo($gpsLat, $gpsLng);",
                                        null
                                    )
                                }
                            }
                            .testTag("map_use_current_gps_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Current Device Location",
                                tint = Color(0xFF002612),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Current Location (My GPS)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF002612)
                            )
                        }
                    }
                }
            }

            // 3. Bottom Selected Pin-Point Details Card + Confirm Button
            Surface(
                color = TacticalNavySurface,
                border = BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEA4335).copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFEA4335),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = initialPlaceTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = initialAddress,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFA2B9CE),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isResolvingAddress) {
                            CircularProgressIndicator(
                                color = TacticalCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Live Coordinates Pill
                    Text(
                        text = String.format(
                            Locale.US,
                            "📌 Pin-Point: Lat %.6f° Long %.6f°",
                            pickedLat,
                            pickedLng
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp
                        ),
                        color = TacticalCyan
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                onConfirmLocation(
                                    pickedLat,
                                    pickedLng,
                                    initialPlaceTitle,
                                    initialAddress
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TacticalAmber,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("confirm_map_pin_button")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Set This Pin-Point Location",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildInteractiveSatelliteMapHtml(initialLat: Double, initialLng: Double): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background: #0d1b2a;
                    font-family: sans-serif;
                }
                .google-watermark {
                    position: absolute;
                    bottom: 10px;
                    left: 12px;
                    z-index: 1000;
                    color: #ffffff;
                    font-size: 20px;
                    font-weight: bold;
                    text-shadow: 1px 2px 4px rgba(0,0,0,0.85);
                    pointer-events: none;
                }
                .crosshair-hint {
                    position: absolute;
                    top: 12px;
                    left: 12px;
                    z-index: 1000;
                    background: rgba(10, 20, 32, 0.82);
                    color: #FFE082;
                    padding: 6px 10px;
                    border-radius: 8px;
                    font-size: 12px;
                    border: 1px solid rgba(0, 229, 255, 0.5);
                    pointer-events: none;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <div class="crosshair-hint">📍 Tap or drag red pin anywhere on Google Map</div>
            <div class="google-watermark">Google</div>
            <script>
                var startLat = $initialLat;
                var startLng = $initialLng;

                var map = L.map('map', {
                    center: [startLat, startLng],
                    zoom: 17,
                    zoomControl: true
                });

                // Google Satellite Hybrid layer (Satellite + Village/Road labels)
                var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
                    maxZoom: 20,
                    attribution: 'Google'
                });

                // Google Street Roadmap layer
                var googleRoadmap = L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                    maxZoom: 20,
                    attribution: 'Google'
                });

                // Fallback Esri Satellite if needed
                var esriSat = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                    maxZoom: 19
                });

                googleHybrid.addTo(map);

                // Custom Red Google Teardrop Pin Icon
                var redPinSvg = '<svg xmlns="http://www.w3.org/2000/svg" width="42" height="54" viewBox="0 0 42 54">' +
                    '<path d="M21 0C9.4 0 0 9.4 0 21c0 15.2 21 33 21 33s21-17.8 21-33C42 9.4 32.6 0 21 0z" fill="#EA4335" stroke="#ffffff" stroke-width="2"/>' +
                    '<circle cx="21" cy="20" r="7.5" fill="#8E1B13"/>' +
                    '</svg>';

                var pinIcon = L.divIcon({
                    html: redPinSvg,
                    className: '',
                    iconSize: [42, 54],
                    iconAnchor: [21, 54]
                });

                var marker = L.marker([startLat, startLng], {
                    draggable: true,
                    icon: pinIcon
                }).addTo(map);

                function notifyAndroid(lat, lng) {
                    if (window.AndroidMapBridge && window.AndroidMapBridge.onPinPicked) {
                        window.AndroidMapBridge.onPinPicked(lat, lng);
                    }
                }

                map.on('click', function(e) {
                    var lat = e.latlng.lat;
                    var lng = e.latlng.lng;
                    marker.setLatLng([lat, lng]);
                    notifyAndroid(lat, lng);
                });

                marker.on('dragend', function(e) {
                    var pos = marker.getLatLng();
                    notifyAndroid(pos.lat, pos.lng);
                });

                window.moveMapTo = function(lat, lng) {
                    marker.setLatLng([lat, lng]);
                    map.setView([lat, lng], 17);
                    notifyAndroid(lat, lng);
                };

                window.setMapLayer = function(mode) {
                    if (mode === 'roadmap') {
                        map.removeLayer(googleHybrid);
                        googleRoadmap.addTo(map);
                    } else {
                        map.removeLayer(googleRoadmap);
                        googleHybrid.addTo(map);
                    }
                };
            </script>
        </body>
        </html>
    """.trimIndent()
}
