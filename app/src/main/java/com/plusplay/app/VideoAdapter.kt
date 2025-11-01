package com.plusplay.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VideoAdapter(
    private val videos: List<VideoFile>,
    private val onVideoClick: (VideoFile) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoName: TextView = view.findViewById(R.id.videoName)
        val videoPath: TextView = view.findViewById(R.id.videoPath)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.videoName.text = video.name
        holder.videoPath.text = video.path
        holder.itemView.setOnClickListener { onVideoClick(video) }
    }

    override fun getItemCount() = videos.size
}
