package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoItem
import com.example.ui.theme.ColorOSGreen80
import com.example.ui.viewmodel.VideoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    video: VideoItem,
    viewModel: VideoViewModel,
    onBack: () -> Unit,
    onEdit: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Flag to lock brightness/screen active state
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Standard video state tracking
    var isPlaying by remember { mutableStateOf(true) }
    var durationMs by remember { mutableStateOf(video.durationMs) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
    // Gestures HUD feedback states
    var brightnessHUDValue by remember { mutableStateOf(-1f) } // -1 means hidden
    var volumeHUDValue by remember { mutableStateOf(-1f) }     // -1 means hidden

    // Local volume and brightness systems query
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    var currentVolume by remember {
        mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }

    var currentBrightness by remember {
        mutableStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f)
    }

    // Star dynamic color subscription
    val isBookmarked by viewModel.isBookmarkedLive(video.path).collectAsState(initial = false)

    // Setup ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(video.path)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    // Apply resume position upon readiness
    LaunchedEffect(exoPlayer) {
        val lastPosition = viewModel.getSavedPlaybackResumePosition(video.path)
        if (lastPosition > 0L) {
            exoPlayer.seekTo(lastPosition)
            Toast.makeText(context, "Resumed playback", Toast.LENGTH_SHORT).show()
        }
    }

    // Update speeds/listeners
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Polling progression timeline position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(video.durationMs)
            delay(250)
        }
    }

    // Playback events listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            // Unsubscribe & Save watch state tracker position
            viewModel.saveToHistory(video, exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Auto-hide controls bar timer
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(5000)
            isControlsVisible = false
        }
    }

    // Handle back button clicks cleanly
    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Gesture Swipe detection boundaries
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { /* Reset HUD display */ },
                    onDragEnd = {
                        scope.launch {
                            delay(1000)
                            brightnessHUDValue = -1f
                            volumeHUDValue = -1f
                        }
                    },
                    onDragCancel = {
                        brightnessHUDValue = -1f
                        volumeHUDValue = -1f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val isLeftSide = change.position.x < (size.width / 2)

                        if (isLeftSide) {
                            // Left side swipe adjusts brightness
                            val updatedBrightness = (currentBrightness - (dragAmount / 800f)).coerceIn(0.01f, 1.0f)
                            currentBrightness = updatedBrightness
                            brightnessHUDValue = updatedBrightness
                            activity?.let {
                                val lp = it.window.attributes
                                lp.screenBrightness = updatedBrightness
                                it.window.attributes = lp
                            }
                        } else {
                            // Right side swipe adjusts volume
                            val deltaVol = -(dragAmount / 150f).roundToInt()
                            val updatedVol = (currentVolume + deltaVol).coerceIn(0, maxVolume)
                            currentVolume = updatedVol
                            volumeHUDValue = updatedVol.toFloat() / maxVolume
                            audioManager.setStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                updatedVol,
                                0
                            )
                        }
                    }
                )
            }
    ) {
        // Player Surface layout wrap
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Tap to toggles controls panel
                    detectTapGestures {
                        isControlsVisible = !isControlsVisible
                    }
                }
                .testTag("exo_player_surface"),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exoPlayer
                    this.resizeMode = resizeMode
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            }
        )

        // Custom Overlapping Render Filters Overlay (If previewing filters in edit trim)
        // (Just base visual controls here for primary playing)

        // Volume / Brightness Swipe HUD Indicator boxes
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = brightnessHUDValue >= 0f,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                HUDAccessoryCard(
                    icon = Icons.Default.Brightness5,
                    label = "Brightness",
                    progress = brightnessHUDValue.coerceIn(0f, 1f)
                )
            }

            AnimatedVisibility(
                visible = volumeHUDValue >= 0f,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                HUDAccessoryCard(
                    icon = if (volumeHUDValue == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    label = "Volume",
                    progress = volumeHUDValue.coerceIn(0f, 1f)
                )
            }
        }

        // Title and top visual header bar controls
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = video.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                // Navigation tools
                Row {
                    IconButton(
                        onClick = { viewModel.toggleBookmark(video) },
                        modifier = Modifier.testTag("player_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) Color.Yellow else Color.White
                        )
                    }

                    IconButton(
                        onClick = { onEdit(video) },
                        modifier = Modifier.testTag("player_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Cut & Edit",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom interactive timeline control card HUD
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                // Formatting timestamps and sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMillis(currentPositionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Slider(
                        value = currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                        onValueChange = {
                            exoPlayer.seekTo(it.toLong())
                            currentPositionMs = it.toLong()
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(100f),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .testTag("player_timeline_slider"),
                        colors = SliderDefaults.colors(
                            activeTrackColor = ColorOSGreen80,
                            thumbColor = ColorOSGreen80
                        )
                    )

                    Text(
                        text = formatMillis(durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control panel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Size mode toggles
                    IconButton(
                        onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            val text = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Scaling: Fit"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Scaling: Stretch"
                                else -> "Scaling: Crop"
                            }
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Default.Fullscreen
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.AspectRatio
                                else -> Icons.Default.CropFree
                            },
                            contentDescription = "Aspect Ratio",
                            tint = Color.White
                        )
                    }

                    // Seek Backward 10s
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Back 10s",
                            tint = Color.White
                        )
                    }

                    // Play Pause FAB
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ColorOSGreen80)
                            .testTag("player_play_pause_fab")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Seek Forward 10s
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(durationMs))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White
                        )
                    }

                    // Speed controller selector
                    IconButton(
                        onClick = {
                            playbackSpeed = when (playbackSpeed) {
                                1.0f -> 1.5f
                                1.5f -> 2.0f
                                2.0f -> 0.5f
                                else -> 1.0f
                            }
                            Toast.makeText(context, "${playbackSpeed}x speed", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = ColorOSGreen80
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HUDAccessoryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    progress: Float
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
        modifier = Modifier
            .size(140.dp)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ColorOSGreen80,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = ColorOSGreen80,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

fun formatMillis(millis: Long): String {
    val totalSecs = (millis / 1000).coerceAtLeast(0)
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}


