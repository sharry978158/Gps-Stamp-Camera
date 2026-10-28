package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.CapturedPhotoItem
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalNavyCard
import com.example.ui.theme.TacticalNavyDark
import com.example.ui.theme.TacticalNavySurface

@Composable
fun GalleryViewerOverlay(
    savedPhotos: List<CapturedPhotoItem>,
    selectedPhoto: CapturedPhotoItem?,
    onSelectPhoto: (CapturedPhotoItem?) -> Unit,
    onCloseGallery: () -> Unit
) {
    val context = LocalContext.current

    BackHandler {
        if (selectedPhoto != null) {
            onSelectPhoto(null)
        } else {
            onCloseGallery()
        }
    }

    Surface(
        color = TacticalNavyDark,
        modifier = Modifier
            .fillMaxSize()
            .testTag("gallery_viewer_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalNavySurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectedPhoto != null) {
                                onSelectPhoto(null)
                            } else {
                                onCloseGallery()
                            }
                        },
                        modifier = Modifier.testTag("gallery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close_button),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = if (selectedPhoto != null) {
                                selectedPhoto.displayName
                            } else {
                                "Saved GPS Photos"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "DCIM/Camera (${savedPhotos.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = TacticalCyan
                        )
                    }
                }

                // Button to open Android system gallery
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                type = "image/*"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    border = BorderStroke(1.dp, TacticalCyan),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("open_system_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = TacticalCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Phone Gallery",
                        style = MaterialTheme.typography.labelMedium,
                        color = TacticalCyan
                    )
                }
            }

            if (selectedPhoto != null) {
                // Full-screen single stamped photo view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black)
                            .border(1.dp, TacticalBorder, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = selectedPhoto.uri,
                            contentDescription = selectedPhoto.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = selectedPhoto.stampSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA2B9CE),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, selectedPhoto.uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share GPS Photo")
                                    )
                                } catch (_: Exception) {
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TacticalCyan,
                                contentColor = Color(0xFF00262C)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Photo")
                        }
                    }
                }
            } else if (savedPhotos.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gps_camera_hero),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = TacticalAmber,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.gallery_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.gallery_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA2B9CE),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Grid of saved GPS-stamped photos
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(savedPhotos, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = TacticalNavyCard),
                            border = BorderStroke(1.dp, TacticalBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPhoto(item) }
                                .testTag("gallery_photo_item_${item.id}")
                        ) {
                            Column {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.78f)
                                )
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.stampSummary,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TacticalAmber,
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
    }
}
