package com.example.data.model

import java.io.File

data class VideoItem(
    val id: String,
    val path: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val resolution: String,
    val folderName: String,
    val dateAddedMs: Long,
    val mimeType: String,
    val isDemo: Boolean = false
) {
    val durationText: String
        get() {
            val totalSecs = durationMs / 1000
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val sizeText: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format("%.2f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

    val fileNameWithExt: String
        get() {
            return try {
                File(path).name
            } catch (e: Exception) {
                title
            }
        }
}
