package com.plusplay.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoFile(
    val name: String,
    val path: String,
    val duration: Long = 0,
    val folderPath: String = ""
) : Parcelable

data class VideoFolder(
    val name: String,
    val path: String,
    val videos: MutableList<VideoFile> = mutableListOf(),
    val subFolders: MutableList<VideoFolder> = mutableListOf()
) {
    fun getFirstVideoPath(): String? {
        // Try to get from direct videos first
        if (videos.isNotEmpty()) {
            return videos.first().path
        }
        // If no direct videos, try to find in subfolders
        for (folder in subFolders) {
            val videoPath = folder.getFirstVideoPath()
            if (videoPath != null) {
                return videoPath
            }
        }
        return null
    }
}
