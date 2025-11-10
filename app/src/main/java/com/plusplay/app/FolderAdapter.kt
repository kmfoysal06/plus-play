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
        val folderIcon: ImageView = view.findViewById(R.id.folderIcon)
        val thumbnailContainer: View = view.findViewById(R.id.thumbnailContainer)
        
        init {
            // Make thumbnail container square
            thumbnailContainer.post {
                val width = thumbnailContainer.width
                val layoutParams = thumbnailContainer.layoutParams
                layoutParams.height = width
                thumbnailContainer.layoutParams = layoutParams
            }
        }
    }

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoName: TextView = view.findViewById(R.id.videoName)
        val videoDuration: TextView = view.findViewById(R.id.videoDuration)
        val videoThumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
        val thumbnailContainer: View = view.findViewById(R.id.thumbnailContainer)
        
        init {
            // Make thumbnail container square
            thumbnailContainer.post {
                val width = thumbnailContainer.width
                val layoutParams = thumbnailContainer.layoutParams
                layoutParams.height = width
                thumbnailContainer.layoutParams = layoutParams
            }
        }
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
                
                // Just use folder icon (no thumbnail loading for better performance)
                holder.folderIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                holder.folderIcon.scaleType = ImageView.ScaleType.CENTER
                holder.folderIcon.alpha = 1.0f
            }
            is VideoViewHolder -> {
                val videoItem = item as ListItem.VideoItem
                holder.videoName.text = videoItem.video.name
                holder.videoDuration.text = formatDuration(videoItem.video.duration)
                holder.itemView.setOnClickListener { onItemClick(item) }
                
                // Reset thumbnail first
                holder.videoThumbnail.setImageResource(android.R.drawable.ic_media_play)
                holder.videoThumbnail.scaleType = ImageView.ScaleType.CENTER
                holder.videoThumbnail.alpha = 0.3f
                
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoPath)
                
                // Try to get frame at the beginning first (0 microseconds)
                var bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                
                // If that fails, try at 1 second
                if (bitmap == null) {
                    bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST)
                }
                
                // If still null, try any frame
                if (bitmap == null) {
                    bitmap = retriever.frameAtTime
                }
                
                retriever.release()
                
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        imageView.setImageBitmap(bitmap)
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.alpha = 1.0f
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep default icon on error
            }
        }
    }
    
}