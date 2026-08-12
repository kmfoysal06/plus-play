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
import android.content.res.ColorStateList
import android.graphics.Color

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

        private const val DEFAULT_FOLDER_ICON = android.R.drawable.ic_menu_gallery
        private const val DEFAULT_VIDEO_ICON = android.R.drawable.ic_media_play
    }

    class BackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val folderName: TextView = view.findViewById(R.id.folderName)
        val backArrow: ImageView = view.findViewById(R.id.backArrow)
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

                if (width > 0) {
                    val layoutParams = thumbnailContainer.layoutParams
                    layoutParams.height = width
                    thumbnailContainer.layoutParams = layoutParams
                }
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

                if (width > 0) {
                    val layoutParams = thumbnailContainer.layoutParams
                    layoutParams.height = width
                    thumbnailContainer.layoutParams = layoutParams
                }
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

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {

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

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val item = items[position]

        when (holder) {

            is BackViewHolder -> {
                holder.folderName.text = "Back"
                holder.backArrow.visibility = View.VISIBLE

                holder.itemView.setOnClickListener {
                    onItemClick(item)
                }
            }

            is FolderViewHolder -> {
                val folderItem = item as ListItem.FolderItem
                val folder = folderItem.folder

                holder.folderName.text = folder.name

                holder.videoCount.text =
                    "${folder.videos.size + folder.subFolders.size} items"

                holder.itemView.setOnClickListener {
                    onItemClick(item)
                }

                /*
                 * IMPORTANT:
                 *
                 * Only look at folder.videos.
                 *
                 * Do NOT use folder.getFirstVideoPath()
                 * because that method also searches inside
                 * subfolders.
                 *
                 * This means:
                 *
                 * Folder/
                 * ├── video.mp4       <- USE
                 * └── SubFolder/
                 *     └── video.mp4    <- IGNORE
                 */

                val directVideo = folder.videos.firstOrNull()

                if (directVideo != null) {

                    // Remove the orange folder-icon tint so the actual
                    // video thumbnail can be displayed.
                    holder.folderIcon.imageTintList = null

                    holder.folderIcon.tag = directVideo.path

                    holder.folderIcon.setImageResource(DEFAULT_FOLDER_ICON)
                    holder.folderIcon.scaleType = ImageView.ScaleType.CENTER
                    holder.folderIcon.alpha = 1.0f

                    loadThumbnail(
                        directVideo.path,
                        holder.folderIcon
                    )

                } else {

                    // No direct video: restore the normal folder icon.
                    holder.folderIcon.tag = null

                    holder.folderIcon.setImageResource(DEFAULT_FOLDER_ICON)

                    holder.folderIcon.imageTintList =
                        ColorStateList.valueOf(Color.rgb(255, 167, 38))

                    holder.folderIcon.scaleType = ImageView.ScaleType.CENTER
                    holder.folderIcon.alpha = 1.0f
                }
            }

            is VideoViewHolder -> {
                val videoItem = item as ListItem.VideoItem

                holder.videoName.text = videoItem.video.name

                holder.videoDuration.text =
                    formatDuration(videoItem.video.duration)

                holder.itemView.setOnClickListener {
                    onItemClick(item)
                }

                /*
                 * Reset thumbnail first because RecyclerView
                 * reuses this ViewHolder.
                 */
                holder.videoThumbnail.tag = videoItem.video.path

                holder.videoThumbnail.setImageResource(
                    DEFAULT_VIDEO_ICON
                )

                holder.videoThumbnail.scaleType =
                    ImageView.ScaleType.CENTER

                holder.videoThumbnail.alpha = 0.3f

                /*
                 * Load the actual video thumbnail asynchronously.
                 */
                loadThumbnail(
                    videoItem.video.path,
                    holder.videoThumbnail
                )
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun updateItems(newItems: List<ListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60

        return String.format(
            "%02d:%02d",
            minutes,
            remainingSeconds
        )
    }

    /**
     * Loads the first useful frame from a video.
     *
     * This method is used for both:
     *
     * 1. Video items
     * 2. Folder thumbnails
     *
     * The ImageView tag is checked before applying the
     * thumbnail so RecyclerView recycling cannot cause
     * the thumbnail to appear on the wrong item.
     */
    private fun loadThumbnail(
        videoPath: String,
        imageView: ImageView
    ) {
        CoroutineScope(Dispatchers.IO).launch {

            var bitmap: Bitmap? = null
            var retriever: MediaMetadataRetriever? = null

            try {
                retriever = MediaMetadataRetriever()

                retriever.setDataSource(videoPath)

                /*
                 * First try the beginning of the video.
                 */
                bitmap = retriever.getFrameAtTime(
                    0,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

                /*
                 * If the first frame cannot be retrieved,
                 * try around one second into the video.
                 */
                if (bitmap == null) {
                    bitmap = retriever.getFrameAtTime(
                        1_000_000,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )
                }

                /*
                 * Final fallback: let Android choose a frame.
                 */
                if (bitmap == null) {
                    bitmap = retriever.frameAtTime
                }

            } catch (e: Exception) {
                e.printStackTrace()

            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (bitmap != null) {

                withContext(Dispatchers.Main) {

                    /*
                     * IMPORTANT:
                     *
                     * RecyclerView may have reused this ImageView
                     * for another video/folder while the thumbnail
                     * was being generated.
                     *
                     * Only apply the bitmap if this ImageView
                     * still belongs to the same video.
                     */
                    if (imageView.tag == videoPath) {
                        imageView.imageTintList = null
                        imageView.setImageBitmap(bitmap)

                        imageView.scaleType =
                            ImageView.ScaleType.CENTER_CROP

                        imageView.alpha = 1.0f
                    }
                }
            }
        }
    }
}