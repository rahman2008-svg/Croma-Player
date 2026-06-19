package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val path: String,
    val title: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val path: String,
    val title: String,
    val durationMs: Long,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_videos", primaryKeys = ["playlistId", "videoPath"])
data class PlaylistVideoEntity(
    val playlistId: Int,
    val videoPath: String,
    val title: String,
    val durationMs: Long,
    val addedTimestamp: Long = System.currentTimeMillis()
)
