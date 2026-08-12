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
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.PopupWindow
import android.media.MediaMetadataRetriever

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

        val storageDirectory = File("/storage")

        storageDirectory.listFiles()?.forEach {
            android.util.Log.d(
                "PLUSPLAY",
                "Storage volume: ${it.absolutePath}"
            )
        }


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
                    0 -> 3  // Back item takes full width (all 3 spans)
                    1 -> 1  // Folder items take 1 span (grid layout like videos)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.action_settings -> {
                showSettingsDrawer()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDrawer() {

        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_settings,
            null
        )

        val popupWindow = PopupWindow(
            view,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setBackgroundDrawable(
            ColorDrawable(Color.WHITE)
        )

        popupWindow.isOutsideTouchable = true

        popupWindow.animationStyle =
            android.R.style.Animation_Dialog

        val checkbox = view.findViewById<CheckBox>(
            R.id.showHiddenFilesCheckbox
        )

        val row = view.findViewById<View>(
            R.id.showHiddenFilesRow
        )

        val exitRow = view.findViewById<View>(
            R.id.exitAppRow
        )

        exitRow.setOnClickListener {

            popupWindow.dismiss()

            showExitConfirmation()
        }

        // Load saved value
        checkbox.isChecked = preferences.getBoolean(
            "show_hidden_files",
            false
        )

        checkbox.setOnCheckedChangeListener { _, isChecked ->

            preferences.edit()
                .putBoolean(
                    "show_hidden_files",
                    isChecked
                )
                .apply()

            // Refresh the current folder
            loadVideos(true)
        }

        row.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
        }

        popupWindow.showAtLocation(
            findViewById(android.R.id.content),
            Gravity.BOTTOM,
            0,
            0
        )
    }

    private fun showExitConfirmation() {

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Exit PlusPlay")
            .setMessage("Are you sure you want to exit the app?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Exit") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private val preferences by lazy {
        getSharedPreferences("plusplay_settings", MODE_PRIVATE)
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
        title = if (folderStack.isEmpty()) "PlusPlay" else folder.name
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

    private fun loadVideos(
        preserveCurrentFolder: Boolean = false
    ) {
        permissionLayout.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        // Remember where the user currently is
        val currentFolderPath = if (
            preserveCurrentFolder &&
            folderStack.isNotEmpty()
        ) {
            folderStack.last().path
        } else {
            null
        }

        Thread {
            allVideos.clear()
            rootFolder.videos.clear()
            rootFolder.subFolders.clear()

            // Don't clear folderStack when we want to preserve location
            if (!preserveCurrentFolder) {
                folderStack.clear()
            }

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

                    if (currentFolderPath != null) {
                        restoreFolder(currentFolderPath)
                    } else {
                        displayFolder(rootFolder)
                    }
                }
            }
        }.start()
    }

    private fun getVideoDuration(videoPath: String): Long {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(videoPath)

            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )

            duration?.toLongOrNull() ?: 0L

        } catch (e: Exception) {
            e.printStackTrace()
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    private fun restoreFolder(path: String) {

        if (path == "/") {
            folderStack.clear()
            displayFolder(rootFolder)
            return
        }

        val newStack = mutableListOf<VideoFolder>()

        fun findFolder(
            folder: VideoFolder,
            targetPath: String
        ): Boolean {

            if (folder.path == targetPath) {
                newStack.add(folder)
                return true
            }

            for (subFolder in folder.subFolders) {
                if (findFolder(subFolder, targetPath)) {
                    newStack.add(0, folder)
                    return true
                }
            }

            return false
        }

        for (folder in rootFolder.subFolders) {
            if (findFolder(folder, path)) {
                break
            }
        }

        if (newStack.isNotEmpty()) {
            folderStack.clear()

            // Don't add Root itself to folderStack
            folderStack.addAll(
                newStack.filter { it.path != "/" }
            )

            displayFolder(folderStack.last())
        } else {
            // Folder disappeared after the refresh.
            folderStack.clear()
            displayFolder(rootFolder)
        }
    }

    private fun scanVideoFiles() {

        val showHiddenFiles = preferences.getBoolean(
            "show_hidden_files",
            false
        )

        val videoSet = mutableSetOf<String>()

        // ---------------------------------------------------------
        // 1. Scan MediaStore
        // ---------------------------------------------------------

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

        cursor?.use {

            val nameColumn =
                it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            val dataColumn =
                it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            val durationColumn =
                it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (it.moveToNext()) {

                val name = it.getString(nameColumn)
                val path = it.getString(dataColumn)
                val duration = it.getLong(durationColumn)

                if (path.isNullOrEmpty()) {
                    continue
                }

                val isHidden = name.startsWith(".")

                val hasValidExtension =
                    SUPPORTED_VIDEO_EXTENSIONS.any { ext ->
                        path.lowercase().endsWith(ext)
                    }

                if (
                    hasValidExtension &&
                    !videoSet.contains(path) &&
                    (showHiddenFiles || !isHidden)
                ) {

                    videoSet.add(path)

                    val file = File(path)
                    val folderPath = file.parent ?: ""
                    allVideos.add(
                        VideoFile(
                            name = name,
                            path = path,
                            duration = duration,
                            folderPath = folderPath
                        )
                    )
                }
            }
        }

        // ---------------------------------------------------------
        // 2. Scan filesystem
        //
        // Only do this when hidden files are enabled.
        // This finds videos that MediaStore doesn't know about.
        // ---------------------------------------------------------

        if (showHiddenFiles) {
            val storageRoots = mutableListOf<File>()

// Internal shared storage
            storageRoots.add(
                android.os.Environment.getExternalStorageDirectory()
            )

// Other mounted storage volumes
            val storageDirectory = File("/storage")

            storageDirectory.listFiles()?.forEach { storageRoot ->

                if (!storageRoot.isDirectory) {
                    return@forEach
                }

                if (
                    storageRoot.name == "self" ||
                    storageRoot.name == "emulated"
                ) {
                    return@forEach
                }

                storageRoots.add(storageRoot)
            }

            // Scan every storage volume
            for (storageRoot in storageRoots) {

                scanFilesystemForVideos(
                    directory = storageRoot,
                    videoSet = videoSet
                )
            }
        }
    }

    private fun scanFilesystemForVideos(
        directory: File,
        videoSet: MutableSet<String>
    ) {

        if (!directory.exists() || !directory.isDirectory) {
            return
        }

        val files = try {
            directory.listFiles()
        } catch (e: SecurityException) {
            null
        }

        if (files == null) {
            return
        }

        for (file in files) {

            try {

                if (file.isDirectory) {

                    scanFilesystemForVideos(
                        directory = file,
                        videoSet = videoSet
                    )

                    continue
                }

                val name = file.name
                val path = file.absolutePath

                val hasValidExtension =
                    SUPPORTED_VIDEO_EXTENSIONS.any { ext ->
                        path.lowercase().endsWith(ext)
                    }

                if (!hasValidExtension) {
                    continue
                }

                // At this point hidden files ARE allowed because
                // this function is only called when the setting is ON.
                if (!videoSet.contains(path)) {

                    videoSet.add(path)

                    val folderPath = file.parent ?: ""

                    val duration = getVideoDuration(path)
                    allVideos.add(
                        VideoFile(
                            name = name,
                            path = path,
                            duration = duration,
                            folderPath = folderPath
                        )
                    )
                }

            } catch (e: SecurityException) {
                // Android denied access to this particular file/folder.
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scanDirectoryForHiddenVideos(
        directory: File,
        videoSet: MutableSet<String>
    ) {

        if (!directory.exists() || !directory.isDirectory) {
            return
        }

        val files = try {
            directory.listFiles()
        } catch (e: SecurityException) {
            null
        }

        files ?: return

        for (file in files) {

            try {

                if (file.isDirectory) {

                    // Recursively scan subdirectories
                    scanDirectoryForHiddenVideos(
                        directory = file,
                        videoSet = videoSet
                    )

                } else {

                    val name = file.name
                    val path = file.absolutePath

                    val isHidden = name.startsWith(".")

                    val hasValidExtension =
                        SUPPORTED_VIDEO_EXTENSIONS.any { ext ->
                            path.lowercase().endsWith(ext)
                        }

                    /*
                     * We only need the filesystem scan for hidden files.
                     *
                     * Normal files have already been obtained from MediaStore.
                     */
                    if (
                        isHidden &&
                        hasValidExtension &&
                        !videoSet.contains(path)
                    ) {

                        videoSet.add(path)

                        val folderPath =
                            file.parent ?: ""
                        val duration = getVideoDuration(path)
                        allVideos.add(
                            VideoFile(
                                name = name,
                                path = path,
                                duration = duration,
                                folderPath = folderPath
                            )
                        )
                    }
                }

            } catch (e: SecurityException) {
                // Ignore directories/files that Android won't let us access.
            } catch (e: Exception) {
                e.printStackTrace()
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
