package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.DraggableMapPreviewOverlay
import com.example.ui.components.FullScreenMapPickerDialog
import com.example.ui.components.GalleryViewerOverlay
import com.example.ui.components.LiveGpsStampCard
import com.example.ui.components.StampCustomizeSheet
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberLight
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalEmerald
import com.example.ui.theme.TacticalNavyCard
import com.example.ui.theme.TacticalNavyDark
import com.example.ui.theme.TacticalNavySurface
import com.example.util.PhotoStampRenderer
import com.example.viewmodel.GpsCameraViewModel
import kotlinx.coroutines.delay

@Composable
fun GpsCameraScreen(
    viewModel: GpsCameraViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraPermissionCheckedOnce by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        cameraPermissionCheckedOnce = true
        val camGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val locGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        hasCameraPermission = camGranted
        hasLocationPermission = locGranted
        if (locGranted) {
            viewModel.startLocationTracking()
        }
    }

    // Automatically request Camera + GPS Location permissions on launch so the app opens straight into the camera
    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.startLocationTracking()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.startLocationTracking()
        }
    }

    // Auto-dismiss banner notifications after 3.2 seconds
    LaunchedEffect(uiState.bannerMessage) {
        if (uiState.bannerMessage != null) {
            delay(3200L)
            viewModel.clearBannerMessage()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // Bind CameraX Preview + ImageCapture whenever camera permission, lens direction, or flash mode changes
    LaunchedEffect(hasCameraPermission, uiState.isBackCamera, uiState.flashMode) {
        if (!hasCameraPermission) return@LaunchedEffect
        imageCapture.flashMode = uiState.flashMode
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (uiState.isBackCamera) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                cameraProvider.unbindAll()
                val hasRequestedCamera = try {
                    cameraProvider.hasCamera(selector)
                } catch (_: Exception) {
                    true
                }

                if (hasRequestedCamera) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                }
            } catch (_: Exception) {
                // If running in an environment without hardware camera support, fallback capture handles it cleanly
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalNavyDark)
            .testTag("gps_camera_root")
    ) {
        // 1. Live Camera Viewfinder Layer (Full Bleed)
        if (hasCameraPermission) {
            // Subtle scenic backdrop behind PreviewView in case virtual camera is black
            FallbackViewfinderBackdrop()
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_preview_view")
            )
        } else {
            FallbackViewfinderBackdrop()
        }

        // 2. Optional Rule-of-Thirds & Tactical Crosshair Grid Overlay
        if (uiState.showGridLines) {
            TacticalGridOverlay()
        }

        // 2b. Small Draggable Live GPS Map Preview Overlay on Camera Screen
        if (uiState.showDraggableMapOverlay) {
            DraggableMapPreviewOverlay(
                stampData = uiState.stampData,
                onRefreshGps = {
                    if (!hasLocationPermission) {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        viewModel.refreshLiveGps()
                    }
                },
                onNormalizedOffsetChanged = { normX, normY ->
                    viewModel.updateMapOverlayNormalizedOffset(normX, normY)
                }
            )
        }

        // 3. Top Tactical HUD Bar (GPS Status Pill, Settings, Google Map Picker, Flash, Grid, Refresh GPS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(TacticalNavyDark.copy(alpha = 0.78f))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Left: Live GPS Status Indicator (Tapping fetches Current Device GPS Location)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            if (!hasLocationPermission) {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                viewModel.refreshLiveGps()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("gps_status_pill")
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = if (uiState.stampData.hasGpsFix && !uiState.stampData.isCustomLocation) TacticalEmerald else TacticalAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            uiState.stampData.isCustomLocation -> "Custom Pin"
                            uiState.stampData.hasGpsFix -> "Live GPS"
                            else -> "Get Current GPS"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp),
                        color = Color.White
                    )
                }

                // Right: Quick Action Icons (Current GPS, Full Google Map Pin-Point, Floating Map, Flash, Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (!hasLocationPermission) {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                viewModel.refreshLiveGps()
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("refresh_gps_button")
                    ) {
                        if (uiState.isRefreshingGps) {
                            CircularProgressIndicator(
                                color = TacticalCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = stringResource(R.string.btn_refresh_gps),
                                tint = TacticalCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleDraggableMapOverlay() },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("toggle_map_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Toggle Draggable Map Overlay",
                            tint = if (uiState.showDraggableMapOverlay) TacticalAmber else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.cycleFlashMode() },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("toggle_flash_button")
                    ) {
                        val (flashIcon, flashTint) = when (uiState.flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn to TacticalAmber
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto to TacticalCyan
                            else -> Icons.Default.FlashOff to Color.White.copy(alpha = 0.7f)
                        }
                        Icon(
                            imageVector = flashIcon,
                            contentDescription = stringResource(R.string.btn_toggle_flash),
                            tint = flashTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings Button (Opens Date, Time & Location Settings)
                    IconButton(
                        onClick = { viewModel.setShowCustomizeSheet(true) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings (Date, Time & Location)",
                            tint = TacticalAmberLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Two Quick Action Pills right below Top Bar:
            // 1) "Google Map Pin Location"
            // 2) "Date & Time Settings"
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Button 1: Open Full-Screen Interactive Google Map Pin-Point Picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TacticalNavySurface.copy(alpha = 0.88f))
                        .border(1.dp, TacticalCyan, RoundedCornerShape(12.dp))
                        .clickable { viewModel.openFullScreenMapPicker() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("open_full_google_map_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = TacticalCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🗺️ Google Map Pin",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        color = Color.White,
                        maxLines = 1
                    )
                }

                // Button 2: Open Settings to Edit Date, Time & Location
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TacticalNavySurface.copy(alpha = 0.88f))
                        .border(
                            1.dp,
                            if (uiState.stampData.customTimestampMillis != null) TacticalAmber else TacticalAmberLight.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.setShowCustomizeSheet(true) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("top_edit_datetime_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditCalendar,
                        contentDescription = null,
                        tint = TacticalAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚙️ Date & Time Settings",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            // Toast / Saved Banner Notification
            AnimatedVisibility(
                visible = uiState.bannerMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = Color(0xEE082E1E),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TacticalEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("saved_toast_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = TacticalEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.bannerMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. Center Permission Card only if user explicitly denied Camera permission
        if (!hasCameraPermission && cameraPermissionCheckedOnce) {
            Surface(
                color = TacticalNavySurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, TacticalCyan),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .testTag("permission_prompt_card")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gps_camera_hero),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.perm_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.perm_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA2B9CE),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            permissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TacticalCyan,
                            contentColor = Color(0xFF00262C)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_permissions_button")
                    ) {
                        Text(
                            text = stringResource(R.string.btn_grant_permissions),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 5. Bottom Dock: Live GPS Stamp Overlay Card + Camera Shutter Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            TacticalNavyDark.copy(alpha = 0.75f),
                            TacticalNavyDark.copy(alpha = 0.95f)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live GPS Stamp Card Preview (tapping satellite map opens Full Google Map Picker; tapping text opens Date/Time/Location Settings)
            LiveGpsStampCard(
                stampData = uiState.stampData,
                currentLiveMillis = uiState.currentLiveMillis,
                onEditClick = { viewModel.setShowCustomizeSheet(true) },
                onMapClick = { viewModel.openFullScreenMapPicker() }
            )

            // Bottom Shutter & Gallery Control Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                // Left: Saved Gallery Thumbnail Button
                val latestPhoto = uiState.savedPhotos.firstOrNull()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TacticalNavyCard)
                        .border(1.5.dp, TacticalCyan.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .clickable { viewModel.setShowGalleryOverlay(true) }
                        .testTag("open_gallery_button")
                ) {
                    if (latestPhoto != null) {
                        AsyncImage(
                            model = latestPhoto.uri,
                            contentDescription = stringResource(R.string.btn_open_gallery),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(R.string.btn_open_gallery),
                            tint = TacticalCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Center: Tactile GPS Camera Shutter Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(3.5.dp, TacticalCyan, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isCapturing) TacticalAmber else Color.White
                        )
                        .clickable(enabled = !uiState.isCapturing) {
                            capturePhotoWithStamp(
                                context = context,
                                hasCameraPermission = hasCameraPermission,
                                imageCapture = imageCapture,
                                previewView = previewView,
                                isFrontCamera = !uiState.isBackCamera,
                                onBitmapCaptured = { bmp ->
                                    viewModel.processAndSaveCapturedBitmap(bmp)
                                }
                            )
                        }
                        .testTag("shutter_capture_button")
                ) {
                    if (uiState.isCapturing) {
                        CircularProgressIndicator(
                            color = TacticalNavyDark,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(TacticalNavyDark.copy(alpha = 0.15f))
                        )
                    }
                }

                // Right: Flip Front / Back Camera Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TacticalNavyCard)
                        .border(1.5.dp, TacticalBorder, RoundedCornerShape(16.dp))
                        .clickable { viewModel.toggleCameraLens() }
                        .testTag("switch_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = stringResource(R.string.btn_switch_camera),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // 6. Shutter White Flash Feedback Overlay
        AnimatedVisibility(
            visible = uiState.showShutterFlash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.75f))
            )
        }

        // 7. Settings Bottom Sheet (Edit Date, Time & GPS Stamp)
        if (uiState.showCustomizeSheet) {
            StampCustomizeSheet(
                stampData = uiState.stampData,
                currentLiveMillis = uiState.currentLiveMillis,
                onDismiss = { viewModel.setShowCustomizeSheet(false) },
                onUpdateDate = { y, m, d -> viewModel.updateCustomDate(y, m, d) },
                onUpdateTime = { h, min -> viewModel.updateCustomTime(h, min) },
                onResetLiveClock = { viewModel.resetToLiveClock() },
                onRefreshLiveGps = {
                    if (!hasLocationPermission) {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        viewModel.refreshLiveGps()
                    }
                },
                onOpenFullScreenMapPicker = { viewModel.openFullScreenMapPicker() },
                onSaveLocationAndMetadata = { title, addr, lat, lng, alt, note, isCustom ->
                    viewModel.updateLocationAndMetadata(title, addr, lat, lng, alt, note, isCustom)
                },
                onSelectTemplate = { viewModel.selectTemplate(it) },
                onSelectCoordinateFormat = { viewModel.selectCoordinateFormat(it) },
                onToggleAltitudeAndAccuracy = { viewModel.toggleAltitudeAndAccuracy(it) },
                onToggleMiniRadar = { viewModel.toggleMiniRadar(it) }
            )
        }

        // 7b. Full-Screen Interactive Google Map Pin-Point Location Picker
        if (uiState.showFullScreenMapPicker) {
            FullScreenMapPickerDialog(
                initialLat = uiState.stampData.latitude,
                initialLng = uiState.stampData.longitude,
                initialPlaceTitle = uiState.mapPickerPreviewTitle.ifBlank { uiState.stampData.placeTitle },
                initialAddress = uiState.mapPickerPreviewAddress.ifBlank { uiState.stampData.addressLine },
                isResolvingAddress = uiState.isResolvingMapPinAddress,
                searchResults = uiState.placeSearchResults,
                isSearchingPlaces = uiState.isSearchingPlaces,
                onSearchQuery = { q -> viewModel.searchPlacesOnMap(q) },
                onClearSearchResults = { viewModel.clearPlaceSearchResults() },
                onMapPinMoved = { lat, lng -> viewModel.onMapPickerPinMoved(lat, lng) },
                onUseCurrentDeviceGps = { onCoords ->
                    if (!hasLocationPermission) {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        viewModel.refreshLiveGps(onCoordinatesFetched = onCoords)
                    }
                },
                onConfirmLocation = { lat, lng, title, addr ->
                    viewModel.confirmMapPinPointLocation(lat, lng, title, addr)
                },
                onDismiss = { viewModel.closeFullScreenMapPicker() }
            )
        }

        // 8. Full-Screen Saved Gallery Overlay
        if (uiState.showGalleryOverlay) {
            GalleryViewerOverlay(
                savedPhotos = uiState.savedPhotos,
                selectedPhoto = uiState.selectedPhotoForPreview,
                onSelectPhoto = { viewModel.selectPhotoForPreview(it) },
                onCloseGallery = { viewModel.setShowGalleryOverlay(false) }
            )
        }
    }
}

private fun capturePhotoWithStamp(
    context: Context,
    hasCameraPermission: Boolean,
    imageCapture: ImageCapture,
    previewView: PreviewView,
    isFrontCamera: Boolean,
    onBitmapCaptured: (Bitmap) -> Unit
) {
    // Play camera shutter click sound for tactile feedback
    try {
        android.media.MediaActionSound().play(android.media.MediaActionSound.SHUTTER_CLICK)
    } catch (_: Exception) {
    }

    // Grab the live viewfinder bitmap right away if available so capture is 100% instant and never hangs
    val liveFrameBitmap = try {
        previewView.bitmap
    } catch (_: Exception) {
        null
    }

    if (liveFrameBitmap != null) {
        onBitmapCaptured(liveFrameBitmap)
        return
    }

    if (!hasCameraPermission) {
        onBitmapCaptured(PhotoStampRenderer.createFallbackSceneBitmap())
        return
    }

    var completed = false
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    val timeoutRunnable = Runnable {
        if (!completed) {
            completed = true
            val fallback = try {
                previewView.bitmap
            } catch (_: Exception) {
                null
            } ?: PhotoStampRenderer.createFallbackSceneBitmap()
            onBitmapCaptured(fallback)
        }
    }
    mainHandler.postDelayed(timeoutRunnable, 900L)

    try {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    if (completed) {
                        image.close()
                        return
                    }
                    completed = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    try {
                        val rotation = image.imageInfo.rotationDegrees
                        val rawBitmap = image.toBitmap()
                        val oriented = PhotoStampRenderer.rotateBitmapIfNeeded(
                            source = rawBitmap,
                            rotationDegrees = rotation,
                            mirrorHorizontal = isFrontCamera
                        )
                        onBitmapCaptured(oriented)
                    } catch (_: Exception) {
                        val fallback = previewView.bitmap ?: PhotoStampRenderer.createFallbackSceneBitmap()
                        onBitmapCaptured(fallback)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (completed) return
                    completed = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val fallback = previewView.bitmap ?: PhotoStampRenderer.createFallbackSceneBitmap()
                    onBitmapCaptured(fallback)
                }
            }
        )
    } catch (_: Exception) {
        if (!completed) {
            completed = true
            mainHandler.removeCallbacks(timeoutRunnable)
            val fallback = previewView.bitmap ?: PhotoStampRenderer.createFallbackSceneBitmap()
            onBitmapCaptured(fallback)
        }
    }
}

@Composable
private fun FallbackViewfinderBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A192F),
                    Color(0xFF173753),
                    Color(0xFF28587B),
                    Color(0xFF0E1D2C)
                )
            )
        )
        // Subtle horizon glow
        drawCircle(
            color = Color(0x22FFB300),
            radius = w * 0.45f,
            center = Offset(w * 0.7f, h * 0.38f)
        )
    }
}

@Composable
private fun TacticalGridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.18f)

        // Rule of thirds vertical lines
        drawLine(
            color = lineColor,
            start = Offset(w / 3f, 0f),
            end = Offset(w / 3f, h),
            strokeWidth = 1.2f
        )
        drawLine(
            color = lineColor,
            start = Offset(2f * w / 3f, 0f),
            end = Offset(2f * w / 3f, h),
            strokeWidth = 1.2f
        )

        // Rule of thirds horizontal lines
        drawLine(
            color = lineColor,
            start = Offset(0f, h / 3f),
            end = Offset(w, h / 3f),
            strokeWidth = 1.2f
        )
        drawLine(
            color = lineColor,
            start = Offset(0f, 2f * h / 3f),
            end = Offset(w, 2f * h / 3f),
            strokeWidth = 1.2f
        )

        // Center optical crosshair
        val cx = w / 2f
        val cy = h * 0.42f
        val crossSize = 24f
        drawLine(
            color = TacticalCyan.copy(alpha = 0.55f),
            start = Offset(cx - crossSize, cy),
            end = Offset(cx + crossSize, cy),
            strokeWidth = 2f
        )
        drawLine(
            color = TacticalCyan.copy(alpha = 0.55f),
            start = Offset(cx, cy - crossSize),
            end = Offset(cx, cy + crossSize),
            strokeWidth = 2f
        )
    }
}
