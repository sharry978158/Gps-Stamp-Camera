package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocationTracker
import com.example.data.PlaceSearchResult
import com.example.model.CapturedPhotoItem
import com.example.model.CoordinateFormat
import com.example.model.GpsStampData
import com.example.model.StampTemplate
import com.example.util.PhotoStampRenderer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

data class GpsCameraUiState(
    val stampData: GpsStampData = GpsStampData(),
    val currentLiveMillis: Long = System.currentTimeMillis(),
    val isBackCamera: Boolean = true,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val showGridLines: Boolean = false,
    val showDraggableMapOverlay: Boolean = false,
    val mapOverlayNormalizedX: Float = 0.56f,
    val mapOverlayNormalizedY: Float = 0.16f,
    val isCapturing: Boolean = false,
    val showShutterFlash: Boolean = false,
    val showCustomizeSheet: Boolean = false,
    val showFullScreenMapPicker: Boolean = false,
    val mapPickerPreviewTitle: String = "",
    val mapPickerPreviewAddress: String = "",
    val isResolvingMapPinAddress: Boolean = false,
    val placeSearchResults: List<PlaceSearchResult> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val showGalleryOverlay: Boolean = false,
    val selectedPhotoForPreview: CapturedPhotoItem? = null,
    val savedPhotos: List<CapturedPhotoItem> = emptyList(),
    val bannerMessage: String? = null,
    val isRefreshingGps: Boolean = false
)

class GpsCameraViewModel(application: Application) : AndroidViewModel(application) {

    private val locationTracker = LocationTracker(application.applicationContext)

    private val _uiState = MutableStateFlow(GpsCameraUiState())
    val uiState: StateFlow<GpsCameraUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var reverseGeocodePinJob: Job? = null

    init {
        // Real-time 1-second device clock ticker so live device date & time stay 100% accurate
        viewModelScope.launch {
            while (isActive) {
                _uiState.update { it.copy(currentLiveMillis = System.currentTimeMillis()) }
                delay(1000L)
            }
        }
        // Load existing saved GPS Stamp photos from MediaStore
        viewModelScope.launch {
            val existing = PhotoStampRenderer.loadSavedPhotosFromGallery(getApplication())
            _uiState.update { it.copy(savedPhotos = existing) }
        }
    }

    fun startLocationTracking() {
        if (locationJob?.isActive == true) return
        // Immediately fetch a single high-accuracy device fix in parallel with continuous updates
        viewModelScope.launch {
            val immediateFix = locationTracker.fetchSingleFix()
            if (immediateFix != null) {
                _uiState.update { current ->
                    if (current.stampData.isCustomLocation) {
                        current
                    } else {
                        current.copy(
                            stampData = current.stampData.copy(
                                latitude = immediateFix.latitude,
                                longitude = immediateFix.longitude,
                                altitudeMeters = immediateFix.altitude,
                                accuracyMeters = immediateFix.accuracy,
                                placeTitle = immediateFix.placeTitle,
                                addressLine = immediateFix.addressLine,
                                isLiveGps = true,
                                hasGpsFix = true
                            )
                        )
                    }
                }
            }
        }

        locationJob = viewModelScope.launch {
            locationTracker.observeLocationUpdates(viewModelScope).collect { snapshot ->
                _uiState.update { current ->
                    if (current.stampData.isCustomLocation) {
                        current
                    } else {
                        current.copy(
                            stampData = current.stampData.copy(
                                latitude = snapshot.latitude,
                                longitude = snapshot.longitude,
                                altitudeMeters = snapshot.altitude,
                                accuracyMeters = snapshot.accuracy,
                                placeTitle = snapshot.placeTitle,
                                addressLine = snapshot.addressLine,
                                isLiveGps = true,
                                hasGpsFix = true
                            )
                        )
                    }
                }
            }
        }
    }

    fun refreshLiveGps(onCoordinatesFetched: ((Double, Double) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshingGps = true,
                    stampData = it.stampData.copy(isCustomLocation = false)
                )
            }
            val fix = locationTracker.fetchSingleFix()
            if (fix != null) {
                _uiState.update { state ->
                    state.copy(
                        isRefreshingGps = false,
                        mapPickerPreviewTitle = fix.placeTitle,
                        mapPickerPreviewAddress = fix.addressLine,
                        stampData = state.stampData.copy(
                            latitude = fix.latitude,
                            longitude = fix.longitude,
                            altitudeMeters = fix.altitude,
                            accuracyMeters = fix.accuracy,
                            placeTitle = fix.placeTitle,
                            addressLine = fix.addressLine,
                            isLiveGps = true,
                            hasGpsFix = true,
                            isCustomLocation = false
                        ),
                        bannerMessage = "📍 Live device GPS location updated"
                    )
                }
                onCoordinatesFetched?.invoke(fix.latitude, fix.longitude)
            } else {
                _uiState.update {
                    it.copy(
                        isRefreshingGps = false,
                        bannerMessage = "Acquiring GPS signal…"
                    )
                }
            }
        }
    }

    fun openFullScreenMapPicker() {
        val current = _uiState.value.stampData
        _uiState.update {
            it.copy(
                showFullScreenMapPicker = true,
                mapPickerPreviewTitle = current.placeTitle,
                mapPickerPreviewAddress = current.addressLine,
                placeSearchResults = emptyList()
            )
        }
    }

    fun closeFullScreenMapPicker() {
        _uiState.update {
            it.copy(
                showFullScreenMapPicker = false,
                placeSearchResults = emptyList()
            )
        }
    }

    fun onMapPickerPinMoved(lat: Double, lng: Double) {
        reverseGeocodePinJob?.cancel()
        reverseGeocodePinJob = viewModelScope.launch {
            _uiState.update { it.copy(isResolvingMapPinAddress = true) }
            val (title, fullAddress) = locationTracker.reverseGeocode(lat, lng)
            _uiState.update {
                it.copy(
                    isResolvingMapPinAddress = false,
                    mapPickerPreviewTitle = title,
                    mapPickerPreviewAddress = fullAddress
                )
            }
        }
    }

    fun searchPlacesOnMap(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingPlaces = true) }
            val results = locationTracker.searchPlacesByName(query)
            _uiState.update {
                it.copy(
                    isSearchingPlaces = false,
                    placeSearchResults = results
                )
            }
        }
    }

    fun clearPlaceSearchResults() {
        _uiState.update { it.copy(placeSearchResults = emptyList()) }
    }

    fun confirmMapPinPointLocation(
        lat: Double,
        lng: Double,
        placeTitle: String,
        addressLine: String
    ) {
        _uiState.update { state ->
            state.copy(
                showFullScreenMapPicker = false,
                stampData = state.stampData.copy(
                    latitude = lat,
                    longitude = lng,
                    placeTitle = placeTitle.ifBlank { state.stampData.placeTitle },
                    addressLine = addressLine.ifBlank { state.stampData.addressLine },
                    isCustomLocation = true
                ),
                bannerMessage = "📌 Pin-point location set: $placeTitle"
            )
        }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isBackCamera = !it.isBackCamera) }
    }

    fun cycleFlashMode() {
        _uiState.update { state ->
            val nextMode = when (state.flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            state.copy(flashMode = nextMode)
        }
    }

    fun toggleGridLines() {
        _uiState.update { it.copy(showGridLines = !it.showGridLines) }
    }

    fun toggleDraggableMapOverlay() {
        _uiState.update { it.copy(showDraggableMapOverlay = !it.showDraggableMapOverlay) }
    }

    fun updateMapOverlayNormalizedOffset(normX: Float, normY: Float) {
        _uiState.update {
            it.copy(
                mapOverlayNormalizedX = normX.coerceIn(0.02f, 0.85f),
                mapOverlayNormalizedY = normY.coerceIn(0.05f, 0.75f)
            )
        }
    }

    fun setShowCustomizeSheet(show: Boolean) {
        _uiState.update { it.copy(showCustomizeSheet = show) }
    }

    fun setShowGalleryOverlay(show: Boolean) {
        _uiState.update {
            it.copy(
                showGalleryOverlay = show,
                selectedPhotoForPreview = if (!show) null else it.selectedPhotoForPreview
            )
        }
    }

    fun selectPhotoForPreview(photo: CapturedPhotoItem?) {
        _uiState.update { it.copy(selectedPhotoForPreview = photo) }
    }

    fun updateCustomDate(year: Int, monthZeroBased: Int, dayOfMonth: Int) {
        _uiState.update { state ->
            val baseMillis = state.stampData.getDisplayTimestampMillis(state.currentLiveMillis)
            val cal = Calendar.getInstance().apply {
                timeInMillis = baseMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, monthZeroBased)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            state.copy(
                stampData = state.stampData.copy(
                    customTimestampMillis = cal.timeInMillis
                )
            )
        }
    }

    fun updateCustomTime(hourOfDay: Int, minute: Int) {
        _uiState.update { state ->
            val baseMillis = state.stampData.getDisplayTimestampMillis(state.currentLiveMillis)
            val cal = Calendar.getInstance().apply {
                timeInMillis = baseMillis
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            state.copy(
                stampData = state.stampData.copy(
                    customTimestampMillis = cal.timeInMillis
                )
            )
        }
    }

    fun resetToLiveClock() {
        _uiState.update { state ->
            state.copy(
                stampData = state.stampData.copy(customTimestampMillis = null),
                bannerMessage = "⏰ Device Live Clock Active"
            )
        }
    }

    fun updateLocationAndMetadata(
        placeTitle: String,
        address: String,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        note: String,
        isCustomLocation: Boolean
    ) {
        _uiState.update { state ->
            state.copy(
                stampData = state.stampData.copy(
                    placeTitle = placeTitle.ifBlank { state.stampData.placeTitle },
                    addressLine = address.ifBlank { state.stampData.addressLine },
                    latitude = latitude,
                    longitude = longitude,
                    altitudeMeters = altitude,
                    customNote = note.trim().ifBlank { "Captured by GPS Map Camera" },
                    isCustomLocation = isCustomLocation
                )
            )
        }
    }

    fun selectTemplate(template: StampTemplate) {
        _uiState.update { state ->
            state.copy(stampData = state.stampData.copy(template = template))
        }
    }

    fun selectCoordinateFormat(format: CoordinateFormat) {
        _uiState.update { state ->
            state.copy(stampData = state.stampData.copy(coordinateFormat = format))
        }
    }

    fun toggleAltitudeAndAccuracy(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(stampData = state.stampData.copy(showAltitudeAndAccuracy = enabled))
        }
    }

    fun toggleMiniRadar(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(stampData = state.stampData.copy(showMiniRadar = enabled))
        }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun processAndSaveCapturedBitmap(rawBitmap: Bitmap) {
        if (_uiState.value.isCapturing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, showShutterFlash = true) }
            delay(140L)
            _uiState.update { it.copy(showShutterFlash = false) }

            val currentState = _uiState.value
            val effectiveMillis = currentState.stampData.getDisplayTimestampMillis(currentState.currentLiveMillis)

            val stamped = PhotoStampRenderer.burnGpsStampOntoBitmap(
                source = rawBitmap,
                stampData = currentState.stampData,
                captureTimeMillis = effectiveMillis
            )

            val savedItem = PhotoStampRenderer.saveStampedBitmapToGallery(
                context = getApplication(),
                stampedBitmap = stamped,
                stampData = currentState.stampData,
                captureTimeMillis = effectiveMillis
            )

            if (savedItem != null) {
                android.widget.Toast.makeText(
                    getApplication(),
                    "Photo saved to device Gallery (DCIM/Camera)!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                _uiState.update { state ->
                    state.copy(
                        isCapturing = false,
                        savedPhotos = listOf(savedItem) + state.savedPhotos,
                        bannerMessage = "✅ Photo saved to device Gallery (DCIM/Camera)!"
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        isCapturing = false,
                        bannerMessage = "Could not save photo to Gallery"
                    )
                }
            }
        }
    }
}
