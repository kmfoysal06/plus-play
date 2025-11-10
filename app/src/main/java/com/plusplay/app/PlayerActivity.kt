package com.plusplay.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var controlsOverlay: View
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var videoTitle: TextView
    private lateinit var seekFeedback: TextView
    private lateinit var subtitleText: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnCenterPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRotate: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var lockedIndicator: ImageButton
    private lateinit var settingsDropdown: LinearLayout
    private lateinit var settingCurrentDirSubtitle: TextView
    private lateinit var settingExternalSubtitle: TextView
    
    private var isPlaying = true
    private var videoPath: String? = null
    private var playlist: ArrayList<VideoFile>? = null
    private var currentVideoIndex = 0
    private var isLocked = false
    private var subtitles: List<SubtitleEntry> = emptyList()
    private var currentSubtitlePath: String? = null
    private var isUserExiting = false // Track if user is intentionally exiting
    
    // Variables for accumulated seeking with scroll tracking
    private var accumulatedSeekTime = 0 // in milliseconds
    private var lastSwipeDirection = 0 // 1 for forward, -1 for backward, 0 for none
    private var lastSwipeTime = 0L
    private val swipeResetDelay = 1000L // Reset accumulated seek after 1 second of no swiping
    
    // Variables for continuous scroll seeking
    private var scrollStartX = 0f
    private var scrollStartY = 0f
    private var totalScrolledDistance = 0f
    private val scrollThresholdPerSeek = 150f // Pixels to scroll for each 10s increment
    private var isScrolling = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            updateSubtitle()
            handler.postDelayed(this, 500)
        }
    }
    
    private val hideControlsRunnable = Runnable {
        hideControls()
    }
    
    private val resetAccumulatedSeekRunnable = Runnable {
        accumulatedSeekTime = 0
        lastSwipeDirection = 0
    }
    
    companion object {
        private const val REQUEST_SUBTITLE_FILE = 100
        private const val PREFS_NAME = "VideoPlayerPrefs"
        private const val KEY_VIDEO_POSITION = "video_position_"
        private const val KEY_WAS_PLAYING = "was_playing_"
        private const val STATE_VIDEO_PATH = "state_video_path"
        private const val STATE_VIDEO_POSITION = "state_video_position"
        private const val STATE_WAS_PLAYING = "state_was_playing"
    }
    
    data class SubtitleEntry(
        val startTime: Long,
        val endTime: Long,
        val text: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable fullscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_player)

        initializeViews()
        
        videoPath = intent.getStringExtra("VIDEO_PATH")
        playlist = intent.getParcelableArrayListExtra("PLAYLIST")
        currentVideoIndex = intent.getIntExtra("VIDEO_INDEX", 0)

        if (videoPath == null) {
            finish()
            return
        }
        
        setVideoTitle()
        setupGestureDetector()
        setupVideoView()
        setupControls()
        loadSubtitles()
    }
    
    private fun initializeViews() {
        videoView = findViewById(R.id.videoView)
        controlsOverlay = findViewById(R.id.controlsOverlay)
        seekBar = findViewById(R.id.seekBar)
        currentTimeText = findViewById(R.id.currentTime)
        totalTimeText = findViewById(R.id.totalTime)
        videoTitle = findViewById(R.id.videoTitle)
        seekFeedback = findViewById(R.id.seekFeedback)
        subtitleText = findViewById(R.id.subtitleText)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnCenterPlayPause = findViewById(R.id.btnCenterPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnRewind = findViewById(R.id.btnRewind)
        btnForward = findViewById(R.id.btnForward)
        btnRotate = findViewById(R.id.btnRotate)
        btnBack = findViewById(R.id.btnBack)
        btnSettings = findViewById(R.id.btnSettings)
        btnLock = findViewById(R.id.btnLock)
        lockedIndicator = findViewById(R.id.lockedIndicator)
        settingsDropdown = findViewById(R.id.settingsDropdown)
        settingCurrentDirSubtitle = findViewById(R.id.settingCurrentDirSubtitle)
        settingExternalSubtitle = findViewById(R.id.settingExternalSubtitle)
    }
    
    private fun setVideoTitle() {
        videoPath?.let { path ->
            val file = File(path)
            videoTitle.text = file.name
        }
    }
    
    private fun setupControls() {
        // Play/Pause buttons
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnCenterPlayPause.setOnClickListener { togglePlayPause() }
        
        // Navigation buttons
        btnPrevious.setOnClickListener { playPreviousVideo() }
        btnNext.setOnClickListener { playNextVideo() }
        
        // Seek buttons
        btnRewind.setOnClickListener { seekRelative(-10000, true) }
        btnForward.setOnClickListener { seekRelative(10000, true) }
        
        // Rotate button
        btnRotate.setOnClickListener { toggleOrientation() }
        
        // Back button
        btnBack.setOnClickListener { 
            isUserExiting = true
            finish() 
        }
        
        // Settings button
        btnSettings.setOnClickListener { toggleSettingsDropdown() }
        
        // Settings - Subtitle from current directory
        settingCurrentDirSubtitle.setOnClickListener {
            settingsDropdown.visibility = View.GONE
            showCurrentDirectorySubtitles()
        }
        
        // Settings - External subtitle
        settingExternalSubtitle.setOnClickListener {
            settingsDropdown.visibility = View.GONE
            openSubtitleFilePicker()
        }
        
        // Lock button
        btnLock.setOnClickListener { toggleLock() }
        
        // Locked indicator (to unlock)
        lockedIndicator.setOnClickListener { toggleLock() }
        
        // SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoView.seekTo(progress)
                    currentTimeText.text = formatTime(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(hideControlsRunnable)
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                scheduleHideControls()
            }
        })
        
        // Update navigation button states
        updateNavigationButtons()
        
        // Show controls initially
        showControls()
    }
    
    private fun toggleSettingsDropdown() {
        settingsDropdown.visibility = if (settingsDropdown.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
        }
        // Keep controls visible when settings are open
        if (settingsDropdown.visibility == View.VISIBLE) {
            handler.removeCallbacks(hideControlsRunnable)
        } else {
            scheduleHideControls()
        }
    }
    
    private fun openSubtitleFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-subrip", "text/plain", "*/*"))
        }
        startActivityForResult(intent, REQUEST_SUBTITLE_FILE)
    }
    
    private fun showCurrentDirectorySubtitles() {
        videoPath?.let { path ->
            val videoFile = File(path)
            val directory = videoFile.parentFile
            
            if (directory != null && directory.exists()) {
                // Find all .srt files in the same directory
                val srtFiles = directory.listFiles { file ->
                    file.isFile && file.extension.equals("srt", ignoreCase = true)
                }?.sortedBy { it.name } ?: emptyList()
                
                if (srtFiles.isEmpty()) {
                    // Show message if no subtitles found
                    AlertDialog.Builder(this)
                        .setTitle("No Subtitles Found")
                        .setMessage("No .srt subtitle files found in the current directory.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // Show dialog with subtitle files
                    val subtitleNames = srtFiles.map { it.name }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Select Subtitle")
                        .setItems(subtitleNames) { dialog, which ->
                            loadSubtitleFile(srtFiles[which])
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }
    
    private fun loadSubtitleFile(file: File) {
        try {
            val content = file.readText()
            subtitles = parseSubtitles(content)
            if (subtitles.isNotEmpty()) {
                subtitleText.visibility = View.VISIBLE
                currentSubtitlePath = file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Failed to load subtitle file: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SUBTITLE_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val content = inputStream?.bufferedReader()?.use { it.readText() }
                    inputStream?.close()
                    
                    content?.let {
                        subtitles = parseSubtitles(it)
                        if (subtitles.isNotEmpty()) {
                            subtitleText.visibility = View.VISIBLE
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun parseSubtitles(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val lines = content.split("\n")
        var i = 0
        
        while (i < lines.size) {
            // Skip subtitle number
            if (lines[i].trim().toIntOrNull() != null) {
                i++
                if (i >= lines.size) break
                
                // Parse time
                val timeLine = lines[i].trim()
                val times = timeLine.split(" --> ")
                if (times.size == 2) {
                    val startTime = parseTime(times[0].trim())
                    val endTime = parseTime(times[1].trim())
                    i++
                    
                    // Parse text
                    val textLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().isNotEmpty()) {
                        textLines.add(lines[i].trim())
                        i++
                    }
                    
                    if (textLines.isNotEmpty()) {
                        entries.add(SubtitleEntry(startTime, endTime, textLines.joinToString("\n")))
                    }
                }
            }
            i++
        }
        
        return entries
    }
    
    private fun parseTime(timeString: String): Long {
        // Format: 00:00:00,000 or 00:00:00.000
        val parts = timeString.replace(",", ".").split(":")
        if (parts.size == 3) {
            val hours = parts[0].toLongOrNull() ?: 0
            val minutes = parts[1].toLongOrNull() ?: 0
            val secondsParts = parts[2].split(".")
            val seconds = secondsParts[0].toLongOrNull() ?: 0
            val millis = if (secondsParts.size > 1) secondsParts[1].toLongOrNull() ?: 0 else 0
            
            return hours * 3600000 + minutes * 60000 + seconds * 1000 + millis
        }
        return 0
    }
    
    private fun updateSubtitle() {
        if (subtitles.isEmpty()) {
            subtitleText.visibility = View.GONE
            return
        }
        
        val currentPosition = videoView.currentPosition.toLong()
        val currentSubtitle = subtitles.find { it.startTime <= currentPosition && currentPosition <= it.endTime }
        
        if (currentSubtitle != null) {
            subtitleText.text = currentSubtitle.text
            subtitleText.visibility = View.VISIBLE
        } else {
            subtitleText.visibility = View.GONE
        }
    }
    
    private fun updateNavigationButtons() {
        val hasPlaylist = playlist != null && playlist!!.size > 1
        btnPrevious.isEnabled = hasPlaylist && currentVideoIndex > 0
        btnNext.isEnabled = hasPlaylist && currentVideoIndex < (playlist?.size ?: 1) - 1
        
        btnPrevious.alpha = if (btnPrevious.isEnabled) 1.0f else 0.3f
        btnNext.alpha = if (btnNext.isEnabled) 1.0f else 0.3f
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isLocked) return false
                
                val screenWidth = videoView.width
                val x = e.x
                val middle = screenWidth / 3f
                
                when {
                    x < middle -> {
                        // Left side - seek backward 10 seconds
                        seekRelative(-10000, true)
                    }
                    x > screenWidth - middle -> {
                        // Right side - seek forward 10 seconds
                        seekRelative(10000, true)
                    }
                    else -> {
                        // Middle - play/pause
                        togglePlayPause()
                    }
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isLocked) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // Check if this is a horizontal scroll
                if (abs(diffX) > abs(diffY) && abs(diffX) > 50) {
                    if (!isScrolling) {
                        // Start new scroll session
                        isScrolling = true
                        scrollStartX = e1.x
                        scrollStartY = e1.y
                        totalScrolledDistance = 0f
                        
                        // Reset accumulated seek for new gesture
                        val currentTime = System.currentTimeMillis()
                        if ((currentTime - lastSwipeTime) >= swipeResetDelay) {
                            accumulatedSeekTime = 0
                            lastSwipeDirection = 0
                        }
                    }
                    
                    val direction = if (diffX > 0) 1 else -1
                    totalScrolledDistance = abs(diffX)
                    
                    // Calculate how many 10s increments based on scroll distance
                    val numIncrements = (totalScrolledDistance / scrollThresholdPerSeek).toInt()
                    val targetSeekTime = numIncrements * 10000 * direction
                    
                    // Only update if we've crossed a new threshold
                    if (targetSeekTime != 0 && targetSeekTime != accumulatedSeekTime) {
                        // Check if direction changed
                        if (direction != lastSwipeDirection && lastSwipeDirection != 0) {
                            // Direction changed, reset
                            accumulatedSeekTime = 0
                        }
                        
                        // Calculate the increment to apply
                        val increment = targetSeekTime - accumulatedSeekTime
                        accumulatedSeekTime = targetSeekTime
                        lastSwipeDirection = direction
                        lastSwipeTime = System.currentTimeMillis()
                        
                        // Apply the seek increment
                        seekRelative(increment, false)
                        
                        // Show feedback with accumulated time
                        showSeekFeedback(accumulatedSeekTime)
                        
                        // Schedule reset of accumulated seek after delay
                        handler.removeCallbacks(resetAccumulatedSeekRunnable)
                        handler.postDelayed(resetAccumulatedSeekRunnable, swipeResetDelay)
                    }
                    
                    return true
                }
                return false
            }
            
            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // Reset scrolling flag on fling
                isScrolling = false
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isLocked) {
                    // When locked, only show/hide the lock indicator
                    lockedIndicator.visibility = if (lockedIndicator.visibility == View.VISIBLE) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                } else {
                    // Toggle controls visibility
                    // Hide settings dropdown if visible
                    if (settingsDropdown.visibility == View.VISIBLE) {
                        settingsDropdown.visibility = View.GONE
                    }
                    toggleControls()
                }
                return true
            }
        })
    }

    private fun setupVideoView() {
        val uri = Uri.parse(videoPath)
        videoView.setVideoURI(uri)
        
        videoView.setOnPreparedListener { mediaPlayer ->
            // Restore saved position if available
            val savedPosition = getSavedPosition()
            if (savedPosition > 0) {
                videoView.seekTo(savedPosition)
            }
            
            // Restore playing state
            val wasPlaying = getWasPlaying()
            if (wasPlaying) {
                mediaPlayer.start()
                isPlaying = true
            } else {
                isPlaying = false
            }
            updatePlayPauseButton()
            
            // Disable looping - video ends and closes player
            mediaPlayer.isLooping = false
            
            // Set seekbar max
            val duration = videoView.duration
            seekBar.max = duration
            totalTimeText.text = formatTime(duration)
            
            // Start updating seekbar
            handler.post(updateSeekBarRunnable)
        }

        videoView.setOnCompletionListener {
            // Clear saved position when video completes
            clearSavedPosition()
            
            if (playlist != null && currentVideoIndex < playlist!!.size - 1) {
                playNextVideo()
            } else {
                isUserExiting = true
                finish()
            }
        }

        videoView.setOnErrorListener { _, what, extra ->
            // Handle error
            isUserExiting = true
            finish()
            true
        }
    }
    
    private fun updateSeekBar() {
        if (videoView.duration > 0) {
            val current = videoView.currentPosition
            seekBar.progress = current
            currentTimeText.text = formatTime(current)
        }
    }
    
    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun loadSubtitles() {
        videoPath?.let { path ->
            val videoFile = File(path)
            val subtitlePath = path.substring(0, path.lastIndexOf('.')) + ".srt"
            val subtitleFile = File(subtitlePath)
            
            if (subtitleFile.exists()) {
                try {
                    val content = subtitleFile.readText()
                    subtitles = parseSubtitles(content)
                    if (subtitles.isNotEmpty()) {
                        subtitleText.visibility = View.VISIBLE
                        currentSubtitlePath = subtitlePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            videoView.pause()
            isPlaying = false
        } else {
            videoView.start()
            isPlaying = true
        }
        updatePlayPauseButton()
        showControls()
    }
    
    private fun updatePlayPauseButton() {
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        btnPlayPause.setImageResource(iconRes)
        btnCenterPlayPause.setImageResource(iconRes)
    }

    private fun seekRelative(milliseconds: Int, showFeedback: Boolean = false) {
        val currentPosition = videoView.currentPosition
        val duration = videoView.duration
        
        // Only seek if both duration and position are available
        if (duration > 0 && currentPosition >= 0) {
            val newPosition = (currentPosition + milliseconds).coerceIn(0, duration)
            videoView.seekTo(newPosition)
            
            if (showFeedback) {
                showSeekFeedback(milliseconds)
            }
        }
    }
    
    private fun showSeekFeedback(milliseconds: Int) {
        val seconds = milliseconds / 1000
        val text = if (seconds > 0) "+${seconds}s" else "${seconds}s"
        seekFeedback.text = text
        seekFeedback.visibility = View.VISIBLE
        
        // Fade out animation
        val fadeOut = AlphaAnimation(1.0f, 0.0f)
        fadeOut.duration = 800
        fadeOut.startOffset = 400
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                seekFeedback.visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        seekFeedback.startAnimation(fadeOut)
    }
    
    private fun playPreviousVideo() {
        if (playlist != null && currentVideoIndex > 0) {
            currentVideoIndex--
            loadVideoAtIndex(currentVideoIndex)
        }
    }
    
    private fun playNextVideo() {
        if (playlist != null && currentVideoIndex < playlist!!.size - 1) {
            currentVideoIndex++
            loadVideoAtIndex(currentVideoIndex)
        }
    }
    
    private fun loadVideoAtIndex(index: Int) {
        playlist?.get(index)?.let { video ->
            videoPath = video.path
            videoTitle.text = video.name
            
            // Stop current playback
            videoView.stopPlayback()
            handler.removeCallbacks(updateSeekBarRunnable)
            
            // Clear subtitles for new video
            subtitles = emptyList()
            subtitleText.visibility = View.GONE
            
            // Load new video
            setupVideoView()
            loadSubtitles()
            
            // Update navigation buttons
            updateNavigationButtons()
        }
    }
    
    private fun toggleOrientation() {
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    
    private fun toggleControls() {
        if (controlsOverlay.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showControls()
        }
    }
    
    private fun showControls() {
        controlsOverlay.visibility = View.VISIBLE
        scheduleHideControls()
    }
    
    private fun hideControls() {
        controlsOverlay.visibility = View.GONE
        settingsDropdown.visibility = View.GONE
        handler.removeCallbacks(hideControlsRunnable)
    }
    
    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3000)
    }
    
    private fun toggleLock() {
        isLocked = !isLocked
        
        if (isLocked) {
            // Hide controls and show lock indicator
            hideControls()
            lockedIndicator.visibility = View.VISIBLE
        } else {
            // Show controls and hide lock indicator
            lockedIndicator.visibility = View.GONE
            showControls()
        }
    }

    private fun toggleSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            toggleSystemUIModern()
        } else {
            toggleSystemUILegacy()
        }
    }

    @Suppress("DEPRECATION")
    private fun toggleSystemUILegacy() {
        val decorView = window.decorView
        val currentVisibility = decorView.systemUiVisibility
        
        if (currentVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
            // Hide system UI
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } else {
            // Show system UI
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun toggleSystemUIModern() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                val systemBars = android.view.WindowInsets.Type.systemBars()
                if (window.decorView.rootWindowInsets?.isVisible(systemBars) == true) {
                    controller.hide(systemBars)
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(systemBars)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Reset scrolling flag when touch is released
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            isScrolling = false
        }
        
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun onPause() {
        super.onPause()
        // Save current position and playing state
        savePosition(videoView.currentPosition)
        saveWasPlaying(isPlaying)
        
        if (isPlaying) {
            videoView.pause()
        }
        handler.removeCallbacks(updateSeekBarRunnable)
        handler.removeCallbacks(hideControlsRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (isPlaying) {
            videoView.start()
        }
        handler.post(updateSeekBarRunnable)
    }

    override fun onBackPressed() {
        isUserExiting = true
        super.onBackPressed()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save state when system kills the activity (e.g., Bluetooth, low memory)
        outState.putString(STATE_VIDEO_PATH, videoPath)
        outState.putInt(STATE_VIDEO_POSITION, videoView.currentPosition)
        outState.putBoolean(STATE_WAS_PLAYING, isPlaying)
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore state when activity is recreated by system
        // This ensures video doesn't restart after Bluetooth or other system events
        val savedPath = savedInstanceState.getString(STATE_VIDEO_PATH)
        if (savedPath != null && savedPath == videoPath) {
            // Same video, restore position
            val savedPos = savedInstanceState.getInt(STATE_VIDEO_POSITION, 0)
            val wasPlaying = savedInstanceState.getBoolean(STATE_WAS_PLAYING, true)
            
            // Save to preferences so setupVideoView can restore
            savePosition(savedPos)
            saveWasPlaying(wasPlaying)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Only clear saved position if user is intentionally exiting
        // Don't clear if system is killing the activity (e.g., Bluetooth, rotation)
        if (isUserExiting || isFinishing) {
            clearSavedPosition()
        }
        videoView.stopPlayback()
        handler.removeCallbacks(updateSeekBarRunnable)
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(resetAccumulatedSeekRunnable)
    }
    
    // Helper methods for saving/loading position
    private fun savePosition(position: Int) {
        videoPath?.let { path ->
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_VIDEO_POSITION + path, position).apply()
        }
    }
    
    private fun getSavedPosition(): Int {
        return videoPath?.let { path ->
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(KEY_VIDEO_POSITION + path, 0)
        } ?: 0
    }
    
    private fun clearSavedPosition() {
        videoPath?.let { path ->
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_VIDEO_POSITION + path).remove(KEY_WAS_PLAYING + path).apply()
        }
    }
    
    private fun saveWasPlaying(wasPlaying: Boolean) {
        videoPath?.let { path ->
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_WAS_PLAYING + path, wasPlaying).apply()
        }
    }
    
    private fun getWasPlaying(): Boolean {
        return videoPath?.let { path ->
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_WAS_PLAYING + path, true) // Default to true (auto-play)
        } ?: true
    }
}
