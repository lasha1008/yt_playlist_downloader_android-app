package com.example.ytdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        setContent {
            // Modern Dark Theme Configuration
            val darkColors = darkColorScheme(
                primary = Color(0xFFE53935), // YouTube Red
                onPrimary = Color.White,
                background = Color(0xFF0F0F0F),
                surface = Color(0xFF1A1A1A),
                onBackground = Color.White,
                onSurface = Color(0xFFEEEEEE),
                surfaceVariant = Color(0xFF272727),
                onSurfaceVariant = Color(0xFFAAAAAA)
            )

            MaterialTheme(colorScheme = darkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DownloadViewModel) {
    val state by viewModel.state.collectAsState()
    var url by remember { mutableStateOf("") }
    var isAudio by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Downloader",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Text(
            text = "Save any video or playlist.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 4.dp, bottom = 32.dp)
        )

        // Only show input fields if not in playlist selection
        AnimatedVisibility(visible = state !is DownloadState.PlaylistSelection) {
            Column {
                // Modern Input Field
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("Paste link here...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { url = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // Segmented Format Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isAudio) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { isAudio = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MP4 Video",
                            fontWeight = if (!isAudio) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isAudio) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAudio) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { isAudio = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MP3 Audio",
                            fontWeight = if (isAudio) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAudio) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Main Download Button
                val isBusy = state is DownloadState.FetchingInfo || state is DownloadState.Downloading || state is DownloadState.Processing
                Button(
                    onClick = {
                        if (url.isNotBlank()) {
                            viewModel.fetchInfo(url, isAudio) // Changed to fetchInfo
                        }
                    },
                    enabled = url.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Dynamic State Area
        AnimatedVisibility(visible = state !is DownloadState.Idle, enter = fadeIn(), exit = fadeOut()) {
            when (val s = state) {
                is DownloadState.Idle -> { }
                is DownloadState.FetchingInfo -> {
                    StatusView(title = "Fetching info...", loading = true)
                }
                is DownloadState.PlaylistSelection -> {
                    PlaylistSelectionView(
                        state = s,
                        onDownloadClicked = { selectedIndices ->
                            if (s.isSpotify) {
                                val queries = s.items.filter { selectedIndices.contains(it.index) }.mapNotNull { it.spotifySearchQuery }
                                viewModel.startDownload(s.url, s.isAudio, null, queries)
                            } else {
                                viewModel.startDownload(s.url, s.isAudio, selectedIndices)
                            }
                        },
                        onCancel = {
                            viewModel.reset()
                        }
                    )
                }
                is DownloadState.Downloading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = s.currentItem, 
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { s.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${String.format("%.1f", s.progress)}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text(s.speed, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text("ETA: ${s.eta}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
                is DownloadState.Processing -> {
                    StatusView(title = "Processing media...", loading = true)
                }
                is DownloadState.Completed -> {
                    val context = LocalContext.current
                    StatusView(
                        title = s.message, 
                        icon = Icons.Default.Check, 
                        iconTint = Color(0xFF4CAF50),
                        actionText = "Open Folder",
                        onAction = {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.type = if (s.isAudio) "audio/*" else "video/*"
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Ignore if no app can handle it
                            }
                        }
                    )
                }
                is DownloadState.Error -> {
                    StatusView(title = s.message, icon = Icons.Default.Warning, iconTint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun PlaylistSelectionView(
    state: DownloadState.PlaylistSelection,
    onDownloadClicked: (List<Int>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedItems by remember { mutableStateOf(state.items.map { it.index }.toSet()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f) // Take up a significant portion of screen
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Playlist: ${state.title}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Select All / Deselect All
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (selectedItems.size == state.items.size) {
                        selectedItems = emptySet()
                    } else {
                        selectedItems = state.items.map { it.index }.toSet()
                    }
                }
                .padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = selectedItems.size == state.items.size,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedItems.size == state.items.size) "Deselect All" else "Select All",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(state.items) { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSelected = selectedItems.toMutableSet()
                            if (newSelected.contains(item.index)) {
                                newSelected.remove(item.index)
                            } else {
                                newSelected.add(item.index)
                            }
                            selectedItems = newSelected
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = selectedItems.contains(item.index),
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (item.thumbnailUrl != null) {
                        coil.compose.AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = "Thumbnail",
                            modifier = Modifier
                                .size(64.dp, 36.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    
                    Text(
                        text = "${item.index}. ${item.title}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onDownloadClicked(selectedItems.toList().sorted()) },
                enabled = selectedItems.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Download (${selectedItems.size})")
            }
        }
    }
}

@Composable
fun StatusView(title: String, loading: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, iconTint: Color = Color.White, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (loading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(actionText, fontWeight = FontWeight.Bold)
            }
        }
    }
}
