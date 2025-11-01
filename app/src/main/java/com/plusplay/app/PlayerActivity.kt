package com.plusplay.app

import android.content.pm.ActivityInfo
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import java.io.File
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
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnCenterPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRotate: ImageButton
    private lateinit var btnBack: ImageButton
    
    private var isPlaying = true
    private var videoPath: String? = null
    private var playlist: ArrayList<VideoFile>? = null
    private var currentVideoIndex = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            handler.postDelayed(this, 500)
        }
    }
    
    private val hideControlsRunnable = Runnable {
        hideControls()
    }

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
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnCenterPlayPause = findViewById(R.id.btnCenterPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnRewind = findViewById(R.id.btnRewind)
        btnForward = findViewById(R.id.btnForward)
        btnRotate = findViewById(R.id.btnRotate)
        btnBack = findViewById(R.id.btnBack)
        
        // Set video title
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
        btnBack.setOnClickListener { finish() }
        
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

            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                if (abs(diffX) > abs(diffY) && abs(diffX) > 100 && abs(velocityX) > 100) {
                    if (diffX > 0) {
                        // Swipe right - forward 10 seconds
                        seekRelative(10000, true)
                    } else {
                        // Swipe left - backward 10 seconds
                        seekRelative(-10000, true)
                    }
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Toggle controls visibility
                toggleControls()
                return true
            }
        })
    }

    private fun setupVideoView() {
        val uri = Uri.parse(videoPath)
        videoView.setVideoURI(uri)
        
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.start()
            isPlaying = true
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
            if (playlist != null && currentVideoIndex < playlist!!.size - 1) {
                playNextVideo()
            } else {
                finish()
            }
        }

        videoView.setOnErrorListener { _, what, extra ->
            // Handle error
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
                // Android's VideoView doesn't support subtitles natively
                // For a minimal implementation, we skip subtitle rendering
                // A full implementation would require MediaPlayer with custom subtitle rendering
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
        handler.removeCallbacks(hideControlsRunnable)
    }
    
    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3000)
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
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun onPause() {
        super.onPause()
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

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback()
        handler.removeCallbacks(updateSeekBarRunnable)
        handler.removeCallbacks(hideControlsRunnable)
    }
}
