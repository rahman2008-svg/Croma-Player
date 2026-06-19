package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.data.database.PlaylistEntity
import com.example.data.database.WatchHistoryEntity
import com.example.data.model.VideoItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun MainScreen(
    viewModel: VideoViewModel,
    onPlayVideo: (VideoItem) -> Unit,
    onEditVideo: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(MainTab.VIDEOS) }

    // State data streams
    val videoList by viewModel.videos.collectAsState()
    val folderMap by viewModel.videoFolders.collectAsState()
    val playlistList by viewModel.playlistList.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Dialog state for adding folders or playlists
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    
    // Bottom Sheet states for appending item to playlist
    var targetAppendVideo by remember { mutableStateOf<VideoItem?>(null) }

    // Media capture launcher: captures camera stream, copies it locally to build actual offline clips!
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val videoUri: Uri? = result.data?.data
            if (videoUri != null) {
                scope.launch {
                    Toast.makeText(context, "Processing recorded clip offline...", Toast.LENGTH_SHORT).show()
                    val savedFile = saveUriToLocalAppStorage(context, videoUri)
                    if (savedFile != null) {
                        viewModel.refreshVideoCatalog()
                        Toast.makeText(context, "Saved to library: ${savedFile.name}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save video file locally", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Video recording canceled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = GraySurface,
                modifier = Modifier.navigationBarsPadding(),
                tonalElevation = 8.dp
            ) {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == tab) tab.activeIcon else tab.inactiveIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CharcoalBg,
                            selectedTextColor = ColorOSGreen80,
                            indicatorColor = ColorOSGreen80,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == MainTab.VIDEOS) {
                FloatingActionButton(
                    onClick = {
                        val recordIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                        try {
                            cameraLauncher.launch(recordIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Camera capture not supported or available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = ColorOSGreen80,
                    contentColor = CharcoalBg,
                    modifier = Modifier.testTag("camera_recorder_fab")
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = "Record Local Video")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CharcoalBg)
        ) {
            // Screen custom search and settings TopBar (for Home, Folders, and Playlists tabs)
            if (currentTab != MainTab.ABOUT) {
                DashboardHeader(viewModel = viewModel)
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ColorOSGreen80)
                    }
                } else {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "TabTransition"
                    ) { tab ->
                        when (tab) {
                            MainTab.VIDEOS -> {
                                VideosTabContent(
                                    videos = videoList,
                                    onPlay = onPlayVideo,
                                    onEdit = onEditVideo,
                                    onAddToPlaylistList = { video -> targetAppendVideo = video },
                                    viewModel = viewModel
                                )
                            }
                            MainTab.FOLDERS -> {
                                FoldersTabContent(
                                    folders = folderMap,
                                    onPlay = onPlayVideo,
                                    onEdit = onEditVideo
                                )
                            }
                            MainTab.PLAYLISTS -> {
                                PlaylistTabContent(
                                    playlists = playlistList,
                                    viewModel = viewModel,
                                    onPlay = onPlayVideo,
                                    onTriggerCreatePlaylist = { showAddPlaylistDialog = true }
                                )
                            }
                            MainTab.HISTORY -> {
                                HistoryTabContent(
                                    historyList = watchHistory,
                                    videoList = videoList,
                                    onPlay = onPlayVideo,
                                    viewModel = viewModel
                                )
                            }
                            MainTab.ABOUT -> {
                                AboutTabContent()
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Playlist Creating Dialog popup
    if (showAddPlaylistDialog) {
        Dialog(onDismissRequest = { showAddPlaylistDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = GraySurface),
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "New Playlist",
                        tint = ColorOSGreen80,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Create Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("e.g. Action Clips", color = Color.Gray) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorOSGreen80,
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_playlist_input")
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddPlaylistDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    viewModel.addPlaylist(newPlaylistName)
                                    newPlaylistName = ""
                                    showAddPlaylistDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorOSGreen40),
                            modifier = Modifier.testTag("submit_playlist_button")
                        ) {
                            Text("Create", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet dialog for assigning a video to an offline playlist
    targetAppendVideo?.let { video ->
        Dialog(onDismissRequest = { targetAppendVideo = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = GraySurface),
                modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add to Offline Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    if (playlistList.isEmpty()) {
                        Text(
                            text = "No custom playlists found. Create one first under the Playlists tab!",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                            items(playlistList) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addVideoToPlaylist(playlist.id, video)
                                            Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                                            targetAppendVideo = null
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FeaturedPlayList, "Playlist", tint = ColorOSGreen80)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(playlist.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { targetAppendVideo = null }) {
                            Text("Close", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

enum class MainTab(val label: String, val activeIcon: androidx.compose.ui.graphics.vector.ImageVector, val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    VIDEOS("Videos", Icons.Default.Movie, Icons.Default.Movie),
    FOLDERS("Folders", Icons.Default.Folder, Icons.Default.FolderOpen),
    PLAYLISTS("Playlists", Icons.Default.FeaturedPlayList, Icons.Default.FeaturedPlayList),
    HISTORY("History", Icons.Default.History, Icons.Default.History),
    ABOUT("About", Icons.Default.Info, Icons.Default.Info)
}

@Composable
fun DashboardHeader(viewModel: VideoViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.selectedSortOrder.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App logo & Beautiful styled branding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ColorOSGreen80),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = CharcoalBg,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Croma",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                ),
                color = Color.White
            )
            Text(
                text = "Player",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif
                ),
                color = ColorOSGreen80
            )
        }

        // Expanded searchable Text Field input box
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("Search...", color = Color.Gray, style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Color.Gray, modifier = Modifier.size(16.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GraySurface,
                unfocusedContainerColor = GraySurface,
                focusedBorderColor = ColorOSGreen80.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("search_input_box"),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Sorting quick dropdown triggers menu
        Box {
            IconButton(
                onClick = { showSortMenu = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(GraySurface, RoundedCornerShape(12.dp))
                    .testTag("sort_filter_button")
            ) {
                Icon(Icons.Default.FilterList, "Sort", tint = ColorOSGreen80, modifier = Modifier.size(18.dp))
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                modifier = Modifier.background(GraySurface).border(1.dp, GrayBorder, RoundedCornerShape(8.dp))
            ) {
                VideoViewModel.SortOrder.values().forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when (order) {
                                    VideoViewModel.SortOrder.NAME_ASC -> "Name: A to Z"
                                    VideoViewModel.SortOrder.NAME_DESC -> "Name: Z to A"
                                    VideoViewModel.SortOrder.DATE_ASC -> "Date: Oldest first"
                                    VideoViewModel.SortOrder.DATE_DESC -> "Date: Newest first"
                                    VideoViewModel.SortOrder.SIZE_ASC -> "Size: Smallest first"
                                    VideoViewModel.SortOrder.SIZE_DESC -> "Size: Largest first"
                                },
                                color = if (sortOrder == order) ColorOSGreen80 else Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        onClick = {
                            viewModel.selectedSortOrder.value = order
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VideosTabContent(
    videos: List<VideoItem>,
    onPlay: (VideoItem) -> Unit,
    onEdit: (VideoItem) -> Unit,
    onAddToPlaylistList: (VideoItem) -> Unit,
    viewModel: VideoViewModel
) {
    val watchHistory by viewModel.watchHistory.collectAsState()

    if (videos.isEmpty()) {
        EmptyLibraryState()
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val recentItems = watchHistory.take(5)
            if (recentItems.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.ndpSafePadding())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = ColorOSGreen80,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recently Watched",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(start = 0.dp, top = 4.dp, end = 12.dp, bottom = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recently_watched_list")
                        ) {
                            items(recentItems, key = { it.path }) { entry ->
                                val matchingVideo = videos.find { it.path == entry.path } ?: VideoItem(
                                    id = entry.path,
                                    path = entry.path,
                                    title = entry.title,
                                    durationMs = entry.durationMs,
                                    sizeBytes = 0L,
                                    resolution = "1080p",
                                    folderName = "History",
                                    dateAddedMs = entry.timestamp,
                                    mimeType = "video/mp4"
                                )
                                RecentVideoCard(
                                    video = matchingVideo,
                                    entry = entry,
                                    onPlay = onPlay
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // All Videos Section Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = ColorOSGreen80,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All Videos",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            items(videos, key = { it.id }) { video ->
                VideoCardItem(
                    video = video,
                    onClick = { onPlay(video) },
                    onEdit = { onEdit(video) },
                    onAddToPlaylist = { onAddToPlaylistList(video) },
                    viewModel = viewModel
                )
            }
        }
    }
}

// Extension to avoid raw dp problems in custom code blocks
private fun Int.ndpSafePadding() = this.dp

@Composable
fun RecentVideoCard(
    video: VideoItem,
    entry: WatchHistoryEntity,
    onPlay: (VideoItem) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GraySurface),
        modifier = Modifier
            .width(160.dp)
            .clickable(onClickLabel = "Play recently watched video") { onPlay(video) }
            .testTag("recent_video_card_${video.id}")
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color.Black)
            ) {
                if (video.isDemo) {
                    Image(
                        painter = painterResource(id = R.drawable.vivid_video_banner),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ColorOSGreen40.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = ColorOSGreen80,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Smooth dark gradient overlay and play icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Progress Bar showing precise local persistence spot
                if (entry.durationMs > 0L) {
                    val progress = entry.lastPositionMs.toFloat() / entry.durationMs.toFloat()
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(4.dp),
                        color = ColorOSGreen80,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Watch status indicator
                val statusText = if (entry.durationMs > 0L) {
                    val percentage = (entry.lastPositionMs * 100 / entry.durationMs).toInt().coerceIn(0, 100)
                    "$percentage% watched"
                } else {
                    "Just watched"
                }

                Text(
                    text = statusText,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun VideoCardItem(
    video: VideoItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onAddToPlaylist: () -> Unit,
    viewModel: VideoViewModel
) {
    var expandedDropdown by remember { mutableStateOf(false) }
    val isBookmarked by viewModel.isBookmarkedLive(video.path).collectAsState(initial = false)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GraySurface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(Color.Black)
            ) {
                // If it is a demo movie, we show our widescreen generic banner or play icon
                if (video.isDemo) {
                    Image(
                        painter = painterResource(id = R.drawable.vivid_video_banner),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Try to load local layout file thumbnail, otherwise fallback to color card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ColorOSGreen40.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = ColorOSGreen80,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Star bookmark badge on top-left
                if (isBookmarked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorited",
                            tint = Color.Yellow,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Duration HUD timeline label on bottom-right of video thumbnail card
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Bottom title and operational buttons section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${video.sizeText} | ${video.resolution}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }

                Box {
                    IconButton(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier.size(32.dp).testTag("video_dropdown_dots")
                    ) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray)
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.background(GraySurface).border(1.dp, GrayBorder, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Video", color = Color.White, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                expandedDropdown = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Crop & Filter Edit", color = Color.White, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                expandedDropdown = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = Color.White, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                expandedDropdown = false
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isBookmarked) "Unbookmark Video" else "Bookmark Video",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            onClick = {
                                expandedDropdown = false
                                viewModel.toggleBookmark(video)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersTabContent(
    folders: Map<String, List<VideoItem>>,
    onPlay: (VideoItem) -> Unit,
    onEdit: (VideoItem) -> Unit
) {
    var expandedFolderKey by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Aesthetic Cinematic banner header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.vivid_video_banner),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        "Manage Directories",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${folders.size} individual smart virtual paths found",
                        color = ColorOSGreen80,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (folders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No folders found", color = Color.Gray, style = MaterialTheme.typography.titleSmall)
                }
            }
        } else {
            folders.forEach { (folderName, itemsList) ->
                val isExpanded = expandedFolderKey == folderName
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedFolderKey = if (isExpanded) null else folderName
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = ColorOSGreen80,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = folderName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${itemsList.size} custom items inside",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }

                        // Child videos list wrapper
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GraySurface.copy(alpha = 0.3f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                itemsList.forEach { videoItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPlay(videoItem) }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircleOutline,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = videoItem.title,
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${videoItem.durationText} | ${videoItem.sizeText}",
                                                color = Color.Gray,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        IconButton(onClick = { onEdit(videoItem) }) {
                                            Icon(Icons.Default.ContentCut, "Edit", tint = ColorOSGreen80, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
fun PlaylistTabContent(
    playlists: List<PlaylistEntity>,
    viewModel: VideoViewModel,
    onPlay: (VideoItem) -> Unit,
    onTriggerCreatePlaylist: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onTriggerCreatePlaylist,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOSGreen40),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("create_playlist_button_trigger")
                ) {
                    Icon(Icons.Default.Add, "Creator", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Playlist", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No custom playlists cataloged",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(playlists) { playlist ->
                var showVideoSublist by remember { mutableStateOf(false) }
                val videosInPlaylist by viewModel.getVideosInPlaylist(playlist.id).collectAsState(initial = emptyList())

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GraySurface)
                        .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVideoSublist = !showVideoSublist }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FeaturedPlayList, "Playlist", tint = ColorOSGreen80, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(playlist.name, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("${videosInPlaylist.size} tracks mapped", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { viewModel.removePlaylist(playlist.id) },
                                modifier = Modifier.testTag("delete_playlist_${playlist.id}")
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { showVideoSublist = !showVideoSublist }) {
                                Icon(
                                    imageVector = if (showVideoSublist) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showVideoSublist,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            if (videosInPlaylist.isEmpty()) {
                                Text(
                                    "This playlist is empty. Add videos from the home grid menu options!",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                videosInPlaylist.forEach { pv ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Create simulated VideoItem to play
                                                onPlay(
                                                    VideoItem(
                                                        id = pv.videoPath,
                                                        path = pv.videoPath,
                                                        title = pv.title,
                                                        durationMs = pv.durationMs,
                                                        sizeBytes = 0L,
                                                        resolution = "1080p",
                                                        folderName = "Playlist",
                                                        dateAddedMs = pv.addedTimestamp,
                                                        mimeType = "video/mp4"
                                                    )
                                                )
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.PlayCircle, "Play", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(pv.title, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        }
                                        IconButton(onClick = { viewModel.removeVideoFromPlaylist(playlist.id, pv.videoPath) }) {
                                            Icon(Icons.Default.Close, "Remove", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTabContent(
    historyList: List<WatchHistoryEntity>,
    videoList: List<VideoItem>,
    onPlay: (VideoItem) -> Unit,
    viewModel: VideoViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently Watched History",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (historyList.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearWatchHistory() }) {
                        Icon(Icons.Default.ClearAll, "Clear", tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (historyList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Playback watch history is empty",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(historyList) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Find the full video mapping if possible, otherwise construct target
                            val matchingVideo = videoList.find { it.path == entry.path } ?: VideoItem(
                                id = entry.path,
                                path = entry.path,
                                title = entry.title,
                                durationMs = entry.durationMs,
                                sizeBytes = 0L,
                                resolution = "1080p",
                                folderName = "History",
                                dateAddedMs = entry.timestamp,
                                mimeType = "video/mp4"
                            )
                            onPlay(matchingVideo)
                        }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ColorOSGreen40.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, "HistoryPlay", tint = ColorOSGreen80)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = entry.title,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val progressPercent = if (entry.durationMs > 0) {
                                (entry.lastPositionMs * 100 / entry.durationMs).toInt()
                            } else 0
                            Text(
                                text = "Progress: ${progressPercent}% | Resume at: ${formatMillis(entry.lastPositionMs)}",
                                color = ColorOSGreen80,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.deleteHistoryEntry(entry.path) }) {
                        Icon(Icons.Default.DeleteOutline, "Remove", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            }
        }
    }
}

@Composable
fun AboutTabContent() {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        
        // Brand Header Section
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ColorOSGreen80),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = CharcoalBg,
                modifier = Modifier.size(44.dp)
            )
        }
        
        Text(
            text = "Croma Player",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            ),
            color = Color.White
        )
        Text(
            text = "Offline Immersive Video Player Clone",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = ColorOSGreen80
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Card 1: About Developer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GraySurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ColorOSGreen80,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "About Developer",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                
                Text(
                    text = "Prince AR Abdur Rahman",
                    color = ColorOSGreen80,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Text(
                    text = "Independent App Developer passionate about building modern Android applications, productivity tools, AI-powered experiences, media players, educational apps, and next-generation digital products.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Contacts
                Text(
                    text = "Get in Touch",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContactRow(
                        icon = Icons.Default.Phone,
                        label = "WhatsApp (Primary)",
                        value = "01707424006",
                        onClick = { uriHandler.openUri("https://wa.me/8801707424006") }
                    )
                    ContactRow(
                        icon = Icons.Default.Phone,
                        label = "WhatsApp (Secondary)",
                        value = "01796951709",
                        onClick = { uriHandler.openUri("https://wa.me/8801796951709") }
                    )
                    ContactRow(
                        icon = Icons.Default.Public,
                        label = "Facebook",
                        value = "facebook.com/share/1BNn32qoJo",
                        onClick = { uriHandler.openUri("https://www.facebook.com/share/1BNn32qoJo/") }
                    )
                    ContactRow(
                        icon = Icons.Default.Link,
                        label = "Instagram",
                        value = "@ur___abdur____rahman__2008",
                        onClick = { uriHandler.openUri("https://www.instagram.com/ur___abdur____rahman__2008") }
                    )
                }
            }
        }

        // Card 2: About Company
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GraySurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = ColorOSGreen80,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "About Company",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "NexVora Lab's Ofc",
                    color = ColorOSGreen80,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "NexVora Lab's Ofc focuses on creating innovative Android applications designed to improve productivity, entertainment, learning, and digital experiences.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Our Mission",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Build fast, beautiful, privacy-friendly, and user-focused applications accessible to everyone.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Card 3: Company Products
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GraySurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = ColorOSGreen80,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Company Products",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                val products = listOf(
                    "NexPlay X", "LifeSphere OS", "Smart Day Planner X",
                    "Study AI", "Lensora Studio", "Offline AI",
                    "NexVora Love Space", "CalcVerse", "NexVoice OS"
                )
                
                ProductsGrid(products)
            }
        }

        // Card 4: Technical & CI/CD Specs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GraySurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = ColorOSGreen80,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Technical Information",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("App Version", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("1.0.0", color = Color.White, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "CI/CD Infrastructure",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                val cicdItems = listOf(
                    "GitHub Actions",
                    "Codemagic CI/CD",
                    "Automated APK Build",
                    "Release Workflow"
                )

                cicdItems.forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ColorOSGreen80,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Card 5: Credits & Footer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GraySurface.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Developed by Prince AR Abdur Rahman",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Published by NexVora Lab's Ofc",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "© 2026 NexVora Lab's Ofc. All Rights Reserved.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ProductsGrid(products: List<String>) {
    Column {
        val chunked = products.chunked(2)
        chunked.forEach { rowProducts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowProducts.forEach { product ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = ColorOSGreen80,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = product,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowProducts.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CharcoalBg)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ColorOSGreen80,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}


@Composable
fun EmptyLibraryState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Subscriptions,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Video database is syncing",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Use the capture video button to record real offline movie clips!",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}


/**
 * Core utility method to read intent camera results and duplicate/save them to 
 * app internal folder storage so they persist safely across sessions completely offline!
 */
private suspend fun saveUriToLocalAppStorage(context: android.content.Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        val rootDir = File(context.filesDir, "VividVideo")
        if (!rootDir.exists()) {
            rootDir.mkdir()
        }
        val fileTarget = File(rootDir, "vivid_camexport_${System.currentTimeMillis()}.mp4")
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(fileTarget).use { outputStream ->
                val buffer = ByteArray(4 * 1024) // 4k chunks
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return@withContext fileTarget
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
