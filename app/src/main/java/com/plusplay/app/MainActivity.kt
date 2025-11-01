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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
    private val videos = mutableListOf<VideoFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.videoRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        permissionLayout = findViewById(R.id.permissionLayout)
        grantPermissionButton = findViewById(R.id.grantPermissionButton)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)

        grantPermissionButton.setOnClickListener {
            requestStoragePermission()
        }

        checkPermissionAndLoadVideos()
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
            videos.clear()
            scanVideoFiles()
            
            runOnUiThread {
                progressBar.visibility = View.GONE
                
                if (videos.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    
                    val adapter = VideoAdapter(videos) { video ->
                        openPlayer(video)
                    }
                    recyclerView.adapter = adapter
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

        // Query all videos, then filter by extension
        val cursor: Cursor? = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        )

        val videoSet = mutableSetOf<String>() // To avoid duplicates

        cursor?.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (it.moveToNext()) {
                val name = it.getString(nameColumn)
                val path = it.getString(dataColumn)
                val duration = it.getLong(durationColumn)
                
                // Check if file has a supported extension
                val hasValidExtension = SUPPORTED_VIDEO_EXTENSIONS.any { ext -> 
                    path.lowercase().endsWith(ext)
                }
                
                if (hasValidExtension && !videoSet.contains(path)) {
                    videoSet.add(path)
                    videos.add(VideoFile(name, path, duration))
                }
            }
        }
        
        // Sort videos by name (case-insensitive)
        videos.sortBy { it.name.lowercase() }
    }

    private fun openPlayer(video: VideoFile) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("VIDEO_PATH", video.path)
        startActivity(intent)
    }
}
