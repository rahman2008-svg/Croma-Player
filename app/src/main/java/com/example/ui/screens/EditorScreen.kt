package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    video: VideoItem,
    viewModel: VideoViewModel,
    onBack: () -> Unit,
    onExportComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Trigger viewModel setup
    LaunchedEffect(video) {
        viewModel.setEditorTarget(video)
    }

    // Capture state flows
    val trimStart by viewModel.activeTrimStartMs.collectAsState()
    val trimEnd by viewModel.activeTrimEndMs.collectAsState()
    val activeFilter by viewModel.activeFilterName.collectAsState()
    val speedUp by viewModel.activeSpeedUp.collectAsState()
    
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()

    var isPlaying by remember { mutableStateOf(true) }
    var rawPlayerPosition by remember { mutableStateOf(0L) }

    // Init Preview ExoPlayer limited to selected trim brackets
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(video.path)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    // Limit playback range to trim start/end
    LaunchedEffect(trimStart, trimEnd) {
        exoPlayer.seekTo(trimStart)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            rawPlayerPosition = exoPlayer.currentPosition
            if (rawPlayerPosition < trimStart || rawPlayerPosition > trimEnd) {
                exoPlayer.seekTo(trimStart)
            }
            delay(100)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    BackHandler {
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0C1013))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Trim & Edit Studio",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }

            // Central Workspace Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Video Player Card Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                this.player = exoPlayer
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        }
                    )

                    // Overlay Canvas Blender representing custom retro aesthetic color filters!
                    if (activeFilter != "Normal") {
                        val filterColor = getFilterColor(activeFilter)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(filterColor.copy(alpha = 0.35f))
                        )
                    }

                    // Playing Action Playback State overlay button
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Preview Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Tiny trim indicator duration text on bottom of card
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Filter: $activeFilter",
                            color = ColorOSGreen80,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Track title & size Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = video.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1
                        )
                        Text(
                            text = "Original duration: ${video.durationText} | Size: ${video.sizeText}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Trimming UI Control Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14191F))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCut, "Trim", tint = ColorOSGreen80, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clip Range Trim", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "Duration: ${formatMillis(trimEnd - trimStart)}",
                                color = ColorOSGreen80,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom double slider simulator representation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Start", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = trimStart.toFloat(),
                                onValueChange = {
                                    val roundedVal = it.toLong()
                                    if (roundedVal < trimEnd - 1000L) {
                                        viewModel.activeTrimStartMs.value = roundedVal
                                    }
                                },
                                valueRange = 0f..video.durationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = ColorOSGreen80,
                                    thumbColor = ColorOSGreen80
                                ),
                                modifier = Modifier.weight(1f).testTag("trim_start_slider")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(formatMillis(trimStart), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("End  ", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = trimEnd.toFloat(),
                                onValueChange = {
                                    val roundedVal = it.toLong()
                                    if (roundedVal > trimStart + 1000L) {
                                        viewModel.activeTrimEndMs.value = roundedVal
                                    }
                                },
                                valueRange = 0f..video.durationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.Red,
                                    thumbColor = Color.Red
                                ),
                                modifier = Modifier.weight(1f).testTag("trim_end_slider")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(formatMillis(trimEnd), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Creative Tone Filters Selector Gallery
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Visual Color Filter",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val filterOptions = listOf(
                        FilterOption("Normal", Color.Transparent),
                        FilterOption("Slate Noir", Color.LightGray),
                        FilterOption("Retro Sepia", Color(0xFFD7CCC8)),
                        FilterOption("Neon Pulse", Color(0xFFE040FB)),
                        FilterOption("Vivid Forest", Color(0xFF69F0AE)),
                        FilterOption("Sunset Glow", Color(0xFFFFAB40)),
                        FilterOption("Cool Ocean", Color(0xFF40C4FF))
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filterOptions) { option ->
                            val isSelected = option.name == activeFilter
                            Column(
                                modifier = Modifier
                                    .width(82.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) ColorOSGreen80.copy(alpha = 0.15f) else Color(0xFF14191F))
                                    .border(
                                        1.dp,
                                        if (isSelected) ColorOSGreen80 else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        viewModel.activeFilterName.value = option.name
                                    }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (option.name == "Normal") Color.DarkGray else option.previewColor)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = option.name,
                                    color = if (isSelected) ColorOSGreen80 else Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Playback speed editor adjustment switches
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14191F))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Fast Export (2x Speed)", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("Speed up playback duration on output", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = speedUp,
                            onCheckedChange = { viewModel.activeSpeedUp.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorOSGreen80,
                                checkedTrackColor = ColorOSGreen80.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Dynamic Export Button
                Button(
                    onClick = {
                        viewModel.triggerVideoExport { file ->
                            Toast.makeText(context, "Export Saved: ${file.name}", Toast.LENGTH_LONG).show()
                            onExportComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("export_save_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOSGreen40),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compile & Export Render", style = MaterialTheme.typography.titleSmall, color = Color.White)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Fullscreen block Overlay rendering loader spinner during compilation
        if (isExporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.width(280.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14191F))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = ColorOSGreen80,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Rendering Visual Timeline...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Applying $activeFilter filter | Trim active",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Progress bar loader
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            color = ColorOSGreen80,
                            trackColor = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${(exportProgress * 100).toInt()}% Done",
                            color = ColorOSGreen80,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

data class FilterOption(val name: String, val previewColor: Color)

fun getFilterColor(name: String): Color {
    return when (name) {
        "Slate Noir" -> Color.DarkGray
        "Retro Sepia" -> Color(0xFFE5D5C5)
        "Neon Pulse" -> Color(0xFFFF2A85)
        "Vivid Forest" -> Color(0xFF00FF7F)
        "Sunset Glow" -> Color(0xFFFF8C00)
        "Cool Ocean" -> Color(0xFF00BFFF)
        else -> Color.Transparent
    }
}
