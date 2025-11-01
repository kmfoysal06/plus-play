package com.plusplay.app

import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var gestureDetector: GestureDetectorCompat
    private var isPlaying = true
    private var videoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable fullscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_player)

        videoView = findViewById(R.id.videoView)
        videoPath = intent.getStringExtra("VIDEO_PATH")

        if (videoPath == null) {
            finish()
            return
        }

        setupGestureDetector()
        setupVideoView()
        loadSubtitles()
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
                        seekRelative(-10000)
                    }
                    x > screenWidth - middle -> {
                        // Right side - seek forward 10 seconds
                        seekRelative(10000)
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
                        seekRelative(10000)
                    } else {
                        // Swipe left - backward 10 seconds
                        seekRelative(-10000)
                    }
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Toggle system UI visibility
                toggleSystemUI()
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
            
            // Disable looping - video ends and closes player
            mediaPlayer.isLooping = false
        }

        videoView.setOnCompletionListener {
            finish()
        }

        videoView.setOnErrorListener { _, what, extra ->
            // Handle error
            finish()
            true
        }
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
    }

    private fun seekRelative(milliseconds: Int) {
        val currentPosition = videoView.currentPosition
        val duration = videoView.duration
        
        // Only seek if both duration and position are available
        if (duration > 0 && currentPosition >= 0) {
            val newPosition = (currentPosition + milliseconds).coerceIn(0, duration)
            videoView.seekTo(newPosition)
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
    }

    override fun onResume() {
        super.onResume()
        if (isPlaying) {
            videoView.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback()
    }
}
