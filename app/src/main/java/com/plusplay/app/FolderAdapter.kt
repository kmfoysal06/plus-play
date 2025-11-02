package com.plusplay.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ListItem {
    data class FolderItem(val folder: VideoFolder) : ListItem()
    data class VideoItem(val video: VideoFile) : ListItem()
    data class BackItem(val text: String) : ListItem()
}

class FolderAdapter(
    private var items: List<ListItem>,
    private val onItemClick: (ListItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_BACK = 0
        private const val TYPE_FOLDER = 1
        private const val TYPE_VIDEO = 2
    }

    class BackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val folderName: TextView = view.findViewById(R.id.folderName)
    }

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val folderName: TextView = view.findViewById(R.id.folderName)
        val videoCount: TextView = view.findViewById(R.id.videoCount)
    }

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoName: TextView = view.findViewById(R.id.videoName)
        val videoDuration: TextView = view.findViewById(R.id.videoDuration)
        val videoThumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.BackItem -> TYPE_BACK
            is ListItem.FolderItem -> TYPE_FOLDER
            is ListItem.VideoItem -> TYPE_VIDEO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_BACK -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_folder, parent, false)
                BackViewHolder(view)
            }
            TYPE_FOLDER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_folder, parent, false)
                FolderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_video, parent, false)
                VideoViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        
        when (holder) {
            is BackViewHolder -> {
                val backItem = item as ListItem.BackItem
                holder.folderName.text = ".. (Go back)"
                holder.itemView.setOnClickListener { onItemClick(item) }
            }
            is FolderViewHolder -> {
                val folderItem = item as ListItem.FolderItem
                holder.folderName.text = folderItem.folder.name
                holder.videoCount.text = "${folderItem.folder.videos.size + folderItem.folder.subFolders.size} items"
                holder.itemView.setOnClickListener { onItemClick(item) }
            }
            is VideoViewHolder -> {
                val videoItem = item as ListItem.VideoItem
                holder.videoName.text = videoItem.video.name
                holder.videoDuration.text = formatDuration(videoItem.video.duration)
                holder.itemView.setOnClickListener { onItemClick(item) }
                
                // Load thumbnail asynchronously
                loadThumbnail(videoItem.video.path, holder.videoThumbnail)
            }
        }
    }

    override fun getItemCount() = items.size
    
    fun updateItems(newItems: List<ListItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    private fun formatDuration(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
    
    private fun loadThumbnail(videoPath: String, imageView: ImageView) {
        // Reset to default icon first
        imageView.setImageResource(android.R.drawable.ic_media_play)
        imageView.alpha = 0.3f
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoPath)
                
                // Get frame at 1 second (1,000,000 microseconds)
                val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                
                bitmap?.let {
                    withContext(Dispatchers.Main) {
                        imageView.setImageBitmap(it)
                        imageView.alpha = 1.0f
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep default icon on error
            }
        }
    }
}