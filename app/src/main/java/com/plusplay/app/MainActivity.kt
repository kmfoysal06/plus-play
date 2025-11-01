package com.plusplay.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private val SUPPORTED_VIDEO_EXTENSIONS = setOf(".mp4", ".mkv", ".avi", ".mov")
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var permissionLayout: View
    private lateinit var grantPermissionButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var folderAdapter: FolderAdapter
    
    private val allVideos = mutableListOf<VideoFile>()
    private val rootFolder = VideoFolder("Root", "/", mutableListOf(), mutableListOf())
    private val folderStack = mutableListOf<VideoFolder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.videoRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        permissionLayout = findViewById(R.id.permissionLayout)
        grantPermissionButton = findViewById(R.id.grantPermissionButton)
        progressBar = findViewById(R.id.progressBar)

        // Use GridLayoutManager with span size lookup for mixed content
        val gridLayoutManager = GridLayoutManager(this, 3)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (folderAdapter.getItemViewType(position)) {
                    0, 1 -> 3  // Back and Folder items take full width (all 3 spans)
                    else -> 1   // Video items take 1 span (grid layout)
                }
            }
        }
        recyclerView.layoutManager = gridLayoutManager
        
        folderAdapter = FolderAdapter(emptyList()) { item ->
            handleItemClick(item)
        }
        recyclerView.adapter = folderAdapter

        grantPermissionButton.setOnClickListener {
            requestStoragePermission()
        }

        checkPermissionAndLoadVideos()
    }
    
    override fun onBackPressed() {
        if (folderStack.isNotEmpty()) {
            folderStack.removeAt(folderStack.size - 1)
            val currentFolder = if (folderStack.isEmpty()) rootFolder else folderStack.last()
            displayFolder(currentFolder)
        } else {
            super.onBackPressed()
        }
    }
    
    private fun handleItemClick(item: ListItem) {
        when (item) {
            is ListItem.BackItem -> {
                onBackPressed()
            }
            is ListItem.FolderItem -> {
                folderStack.add(item.folder)
                displayFolder(item.folder)
            }
            is ListItem.VideoItem -> {
                openPlayer(item.video)
            }
        }
    }
    
    private fun displayFolder(folder: VideoFolder) {
        val items = mutableListOf<ListItem>()
        
        // Add back button if not at root
        if (folderStack.isNotEmpty()) {
            items.add(ListItem.BackItem(".."))
        }
        
        // Add subfolders first
        folder.subFolders.sortedBy { it.name.lowercase() }.forEach {
            items.add(ListItem.FolderItem(it))
        }
        
        // Add videos
        folder.videos.sortedBy { it.name.lowercase() }.forEach {
            items.add(ListItem.VideoItem(it))
        }
        
        folderAdapter.updateItems(items)
        
        // Update title
        title = if (folderStack.isEmpty()) "Plus Play" else folder.name
    }

    private fun checkPermissionAndLoadVideos() {
        if (hasStoragePermission()) {
            loadVideos()
        } else {
            showPermissionLayout()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadVideos()
            } else {
                showPermissionLayout()
            }
        }
    }

    private fun showPermissionLayout() {
        permissionLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun loadVideos() {
        permissionLayout.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        
        Thread {
            allVideos.clear()
            rootFolder.videos.clear()
            rootFolder.subFolders.clear()
            folderStack.clear()
            
            scanVideoFiles()
            organizeFolders()
            
            runOnUiThread {
                progressBar.visibility = View.GONE
                
                if (allVideos.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    displayFolder(rootFolder)
                }
            }
        }.start()
    }

    private fun scanVideoFiles() {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION
        )

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        )

        val videoSet = mutableSetOf<String>()

        cursor?.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (it.moveToNext()) {
                val name = it.getString(nameColumn)
                val path = it.getString(dataColumn)
                val duration = it.getLong(durationColumn)
                
                val hasValidExtension = SUPPORTED_VIDEO_EXTENSIONS.any { ext -> 
                    path.lowercase().endsWith(ext)
                }
                
                if (hasValidExtension && !videoSet.contains(path)) {
                    videoSet.add(path)
                    val file = File(path)
                    val folderPath = file.parent ?: ""
                    allVideos.add(VideoFile(name, path, duration, folderPath))
                }
            }
        }
    }
    
    private fun organizeFolders() {
        val folderMap = mutableMapOf<String, VideoFolder>()
        
        // Group videos by folder
        allVideos.forEach { video ->
            val folderPath = video.folderPath
            
            if (folderPath.isNotEmpty()) {
                val folder = folderMap.getOrPut(folderPath) {
                    val folderFile = File(folderPath)
                    VideoFolder(folderFile.name, folderPath, mutableListOf(), mutableListOf())
                }
                folder.videos.add(video)
            }
        }
        
        // Build folder hierarchy
        folderMap.values.forEach { folder ->
            val parentPath = File(folder.path).parent
            
            if (parentPath != null && folderMap.containsKey(parentPath)) {
                // Add to parent folder
                folderMap[parentPath]?.subFolders?.add(folder)
            } else {
                // Add to root
                rootFolder.subFolders.add(folder)
            }
        }
    }

    private fun openPlayer(video: VideoFile) {
        // Get videos in current folder for playlist
        val currentFolder = if (folderStack.isEmpty()) rootFolder else folderStack.last()
        val playlist = ArrayList(currentFolder.videos)
        val videoIndex = playlist.indexOfFirst { it.path == video.path }
        
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("VIDEO_PATH", video.path)
        intent.putParcelableArrayListExtra("PLAYLIST", playlist)
        intent.putExtra("VIDEO_INDEX", videoIndex)
        startActivity(intent)
    }
}
