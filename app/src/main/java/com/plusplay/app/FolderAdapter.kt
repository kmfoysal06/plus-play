package com.plusplay.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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
        val videoPath: TextView = view.findViewById(R.id.videoPath)
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
                holder.folderName.text = backItem.text
                holder.itemView.setOnClickListener { onItemClick(item) }
            }
            is FolderViewHolder -> {
                val folderItem = item as ListItem.FolderItem
                holder.folderName.text = "📁 ${folderItem.folder.name}"
                holder.videoCount.text = "${folderItem.folder.videos.size + folderItem.folder.subFolders.size} items"
                holder.itemView.setOnClickListener { onItemClick(item) }
            }
            is VideoViewHolder -> {
                val videoItem = item as ListItem.VideoItem
                holder.videoName.text = videoItem.video.name
                holder.videoPath.text = videoItem.video.path
                holder.itemView.setOnClickListener { onItemClick(item) }
            }
        }
    }

    override fun getItemCount() = items.size
    
    fun updateItems(newItems: List<ListItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}