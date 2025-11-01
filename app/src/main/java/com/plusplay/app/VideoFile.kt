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
)
