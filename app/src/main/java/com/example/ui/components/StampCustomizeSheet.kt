package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.CoordinateFormat
import com.example.model.GpsStampData
import com.example.model.StampTemplate
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberLight
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalNavyCard
import com.example.ui.theme.TacticalNavySurface
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StampCustomizeSheet(
    stampData: GpsStampData,
    currentLiveMillis: Long,
    onDismiss: () -> Unit,
    onUpdateDate: (year: Int, monthZeroBased: Int, dayOfMonth: Int) -> Unit,
    onUpdateTime: (hourOfDay: Int, minute: Int) -> Unit,
    onResetLiveClock: () -> Unit,
    onRefreshLiveGps: () -> Unit,
    onOpenFullScreenMapPicker: () -> Unit,
    onSaveLocationAndMetadata: (
        placeTitle: String,
        address: String,
        lat: Double,
        lng: Double,
        alt: Double,
        note: String,
        isCustomLocation: Boolean
    ) -> Unit,
    onSelectTemplate: (StampTemplate) -> Unit,
    onSelectCoordinateFormat: (CoordinateFormat) -> Unit,
    onToggleAltitudeAndAccuracy: (Boolean) -> Unit,
    onToggleMiniRadar: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var placeTitleInput by remember(stampData.placeTitle) { mutableStateOf(stampData.placeTitle) }
    var addressInput by remember(stampData.addressLine) { mutableStateOf(stampData.addressLine) }
    var latInput by remember(stampData.latitude) {
        mutableStateOf(String.format(Locale.US, "%.6f", stampData.latitude))
    }
    var lngInput by remember(stampData.longitude) {
        mutableStateOf(String.format(Locale.US, "%.6f", stampData.longitude))
    }
    var altInput by remember(stampData.altitudeMeters) {
        mutableStateOf(String.format(Locale.US, "%.1f", stampData.altitudeMeters))
    }
    var noteInput by remember(stampData.customNote) { mutableStateOf(stampData.customNote) }
    var locationEditedManually by remember(stampData.isCustomLocation) {
        mutableStateOf(stampData.isCustomLocation)
    }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val displayMillis = stampData.getDisplayTimestampMillis(currentLiveMillis)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TacticalNavySurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sheet_title_customize),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.sheet_subtitle_customize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA2B9CE)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_customize_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close_button),
                        tint = Color.White
                    )
                }
            }

            HorizontalDivider(color = TacticalBorder)

            // SECTION 1: Date & Time Editor
            Surface(
                color = TacticalNavyCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (stampData.customTimestampMillis != null) TacticalAmber else TacticalCyan.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📅 Date & Time Settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TacticalAmberLight
                        )
                        Surface(
                            color = if (stampData.customTimestampMillis != null) {
                                TacticalAmber.copy(alpha = 0.25f)
                            } else {
                                TacticalCyan.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (stampData.customTimestampMillis != null) "Custom Time" else "Live Device Time",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (stampData.customTimestampMillis != null) TacticalAmberLight else TacticalCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = stampData.formatFullGpsMapDateTime(currentLiveMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePickerDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, TacticalCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_date_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = TacticalCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "Change Date",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFA2B9CE)
                                )
                                Text(
                                    text = stampData.formatDateShort(currentLiveMillis),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showTimePickerDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, TacticalAmber),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_time_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = TacticalAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "Change Time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFA2B9CE)
                                )
                                Text(
                                    text = stampData.formatTime(currentLiveMillis),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (stampData.customTimestampMillis != null) {
                        TextButton(
                            onClick = onResetLiveClock,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("reset_live_clock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = TacticalCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_reset_live_time),
                                color = TacticalCyan
                            )
                        }
                    }
                }
            }

            // SECTION 2: GPS Location & Coordinates Editor
            Surface(
                color = TacticalNavyCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📍 Location Settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TacticalCyan
                        )

                        Surface(
                            color = if (locationEditedManually) TacticalAmber.copy(alpha = 0.25f) else TacticalCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (locationEditedManually) "Custom Pin" else "Live GPS",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (locationEditedManually) TacticalAmberLight else TacticalCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Two prominent action buttons: 1) Open Full Google Map to Pin-Point 2) Use Current Device GPS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenFullScreenMapPicker()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TacticalAmber,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.25f)
                                .testTag("sheet_open_google_map_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🗺️ Pin on Google Map",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                locationEditedManually = false
                                onRefreshLiveGps()
                            },
                            border = BorderStroke(1.dp, TacticalCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_refresh_gps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = TacticalCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Current GPS",
                                color = TacticalCyan,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    OutlinedTextField(
                        value = placeTitleInput,
                        onValueChange = {
                            placeTitleInput = it
                            locationEditedManually = true
                        },
                        label = { Text("City / Village Title (e.g. Khawaja Wardag, Punjab, India)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TacticalCyan,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = TacticalCyan,
                            unfocusedLabelColor = Color(0xFFA2B9CE)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_place_title_field")
                    )

                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = {
                            addressInput = it
                            locationEditedManually = true
                        },
                        label = { Text(stringResource(R.string.label_address)) },
                        singleLine = false,
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TacticalCyan,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = TacticalCyan,
                            unfocusedLabelColor = Color(0xFFA2B9CE)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_address_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = latInput,
                            onValueChange = {
                                latInput = it
                                locationEditedManually = true
                            },
                            label = { Text(stringResource(R.string.label_latitude)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TacticalCyan,
                                unfocusedBorderColor = TacticalBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = TacticalCyan,
                                unfocusedLabelColor = Color(0xFFA2B9CE)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_latitude_field")
                        )

                        OutlinedTextField(
                            value = lngInput,
                            onValueChange = {
                                lngInput = it
                                locationEditedManually = true
                            },
                            label = { Text(stringResource(R.string.label_longitude)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TacticalCyan,
                                unfocusedBorderColor = TacticalBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = TacticalCyan,
                                unfocusedLabelColor = Color(0xFFA2B9CE)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_longitude_field")
                        )
                    }

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text(stringResource(R.string.label_note)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TacticalAmber,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = TacticalAmber,
                            unfocusedLabelColor = Color(0xFFA2B9CE)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_note_field")
                    )
                }
            }

            // SECTION 3: Map & Format Options
            Surface(
                color = TacticalNavyCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoordinateFormat.entries.forEach { format ->
                            val selected = stampData.coordinateFormat == format
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectCoordinateFormat(format) },
                                label = { Text(format.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TacticalAmber.copy(alpha = 0.25f),
                                    selectedLabelColor = TacticalAmberLight,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Show Satellite Map Box",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = stampData.showMiniRadar,
                            onCheckedChange = onToggleMiniRadar,
                            colors = SwitchDefaults.colors(checkedThumbColor = TacticalAmber)
                        )
                    }
                }
            }

            // Apply / Save Button
            Button(
                onClick = {
                    val parsedLat = latInput.toDoubleOrNull() ?: stampData.latitude
                    val parsedLng = lngInput.toDoubleOrNull() ?: stampData.longitude
                    val parsedAlt = altInput.toDoubleOrNull() ?: stampData.altitudeMeters
                    onSaveLocationAndMetadata(
                        placeTitleInput,
                        addressInput,
                        parsedLat,
                        parsedLng,
                        parsedAlt,
                        noteInput,
                        locationEditedManually
                    )
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TacticalCyan,
                    contentColor = Color(0xFF00262C)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("apply_stamp_changes_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_save_changes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = displayMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedUtcMillis = datePickerState.selectedDateMillis
                        if (selectedUtcMillis != null) {
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = selectedUtcMillis
                            }
                            onUpdateDate(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH)
                            )
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_date_button")
                ) {
                    Text("OK", color = TacticalCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePickerDialog) {
        val cal = Calendar.getInstance().apply { timeInMillis = displayMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        Dialog(onDismissRequest = { showTimePickerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = TacticalNavySurface,
                border = BorderStroke(1.dp, TacticalAmber),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Time",
                        style = MaterialTheme.typography.titleLarge,
                        color = TacticalAmberLight
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePickerDialog = false }) {
                            Text("Cancel", color = Color(0xFFA2B9CE))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onUpdateTime(timePickerState.hour, timePickerState.minute)
                                showTimePickerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TacticalAmber,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("confirm_time_button")
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
