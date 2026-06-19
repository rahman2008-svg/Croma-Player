package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.PlaylistEntity
import com.example.data.database.WatchHistoryEntity
import com.example.data.model.VideoItem
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = VideoRepository(
        context = application,
        watchHistoryDao = database.watchHistoryDao(),
        bookmarkDao = database.bookmarkDao(),
        playlistDao = database.playlistDao()
    )

    // Watch history lists
    val watchHistory: StateFlow<List<WatchHistoryEntity>> = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookmark lists
    val bookmarkList: StateFlow<List<VideoItem>> = combine(
        repository.bookmarks,
        flow { emit(repository.scanLocalVideos()) } // Refreshing scan
    ) { bookmarks, localVideos ->
        val bookmarkedPaths = bookmarks.map { it.path }.toSet()
        localVideos.filter { it.path in bookmarkedPaths }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Custom playlists
    val playlistList: StateFlow<List<PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and Sort states
    val searchQuery = MutableStateFlow("")
    val selectedSortOrder = MutableStateFlow(SortOrder.DATE_DESC)

    // Raw scanned videos
    private val _rawVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val isLoading = MutableStateFlow(false)

    // Refined, processed, sorted, filtered list of videos
    val videos: StateFlow<List<VideoItem>> = combine(
        _rawVideos,
        searchQuery,
        selectedSortOrder
    ) { raw, query, sort ->
        var filteredList = if (query.isEmpty()) {
            raw
        } else {
            raw.filter { it.title.contains(query, ignoreCase = true) }
        }

        filteredList = when (sort) {
            SortOrder.NAME_ASC -> filteredList.sortedBy { it.title.lowercase() }
            SortOrder.NAME_DESC -> filteredList.sortedByDescending { it.title.lowercase() }
            SortOrder.DATE_ASC -> filteredList.sortedBy { it.dateAddedMs }
            SortOrder.DATE_DESC -> filteredList.sortedByDescending { it.dateAddedMs }
            SortOrder.SIZE_ASC -> filteredList.sortedBy { it.sizeBytes }
            SortOrder.SIZE_DESC -> filteredList.sortedByDescending { it.sizeBytes }
        }

        filteredList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Grouping by folders
    val videoFolders: StateFlow<Map<String, List<VideoItem>>> = videos
        .map { list -> list.groupBy { it.folderName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Active playing video item configuration
    private val _currentPlayingVideo = MutableStateFlow<VideoItem?>(null)
    val currentPlayingVideo: StateFlow<VideoItem?> = _currentPlayingVideo.asStateFlow()

    // Editor target item state
    private val _editorTargetVideo = MutableStateFlow<VideoItem?>(null)
    val editorTargetVideo: StateFlow<VideoItem?> = _editorTargetVideo.asStateFlow()

    var activeTrimStartMs = MutableStateFlow(0L)
    var activeTrimEndMs = MutableStateFlow(10000L)
    var activeFilterName = MutableStateFlow("Normal")
    var activeSpeedUp = MutableStateFlow(false)

    // Exporting progress loading
    val isExporting = MutableStateFlow(false)
    val exportProgress = MutableStateFlow(0f)
    val lastExportedFile = MutableStateFlow<File?>(null)

    enum class SortOrder {
        NAME_ASC, NAME_DESC,
        DATE_ASC, DATE_DESC,
        SIZE_ASC, SIZE_DESC
    }

    init {
        refreshVideoCatalog()
    }

    /**
     * Scan storage and directories immediately
     */
    fun refreshVideoCatalog() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val scanned = repository.scanLocalVideos()
                _rawVideos.value = scanned
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Refresh failed: ${e.message}", e)
            } finally {
                isLoading.value = false
            }
        }
    }

    /**
     * Handle Player configuration
     */
    fun setPlayingVideo(video: VideoItem?) {
        _currentPlayingVideo.value = video
        if (video != null) {
            // Log details and record immediately
            saveToHistory(video, 0L)
        }
    }

    fun isBookmarkedLive(path: String): Flow<Boolean> {
        return repository.isBookmarked(path)
    }

    fun toggleBookmark(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleBookmark(video)
        }
    }

    fun saveToHistory(video: VideoItem, currentPosMs: Long) {
        viewModelScope.launch {
            repository.saveWatchHistory(
                path = video.path,
                title = video.title,
                positionMs = currentPosMs,
                durationMs = video.durationMs
            )
        }
    }

    suspend fun getSavedPlaybackResumePosition(path: String): Long {
        return repository.getHistoryPosition(path)
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteHistoryEntry(path: String) {
        viewModelScope.launch {
            repository.deleteHistoryEntry(path)
        }
    }

    /**
     * Playlist handling
     */
    fun addPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun removePlaylist(id: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addVideoToPlaylist(playlistId: Int, video: VideoItem) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, video)
        }
    }

    fun removeVideoFromPlaylist(playlistId: Int, videoPath: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, videoPath)
        }
    }

    fun getVideosInPlaylist(playlistId: Int) = repository.getVideosInPlaylist(playlistId)

    /**
     * Editor Trimming & Filtering configurations
     */
    fun setEditorTarget(video: VideoItem) {
        _editorTargetVideo.value = video
        activeTrimStartMs.value = 0L
        activeTrimEndMs.value = video.durationMs.coerceAtMost(30000L) // limit trim preview block width
        activeFilterName.value = "Normal"
        activeSpeedUp.value = false
    }

    fun triggerVideoExport(onComplete: (File) -> Unit) {
        val target = _editorTargetVideo.value ?: return
        viewModelScope.launch {
            isExporting.value = true
            exportProgress.value = 0.05f
            
            // Simulating offline editing rendering
            for (i in 1..20) {
                kotlinx.coroutines.delay(120)
                exportProgress.value = (i / 20f)
            }
            
            val exported = repository.saveProcessedVideoFile(
                originalName = target.title,
                startMs = activeTrimStartMs.value,
                endMs = activeTrimEndMs.value,
                isSpeedUp = activeSpeedUp.value,
                filterName = activeFilterName.value
            )
            
            lastExportedFile.value = exported
            refreshVideoCatalog() // Reload to catch the local export
            isExporting.value = false
            onComplete(exported)
        }
    }
}
