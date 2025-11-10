# Implementation Details

## Application Structure

### MainActivity
- Requests and handles storage permissions (Android 13+ and legacy support)
- Scans device storage for video files using MediaStore API
- Displays videos in a RecyclerView with name and path
- Handles empty state and permission denied scenarios
- Launches PlayerActivity when a video is tapped

### PlayerActivity
- Full-screen video playback using VideoView
- Gesture detection:
  - **Swipe left**: Seek backward 10 seconds
  - **Swipe right**: Seek forward 10 seconds
  - **Double-tap left third**: Seek backward 10 seconds
  - **Double-tap right third**: Seek forward 10 seconds
  - **Double-tap middle third**: Toggle play/pause
  - **Single tap**: Toggle system UI visibility
- Auto-loads .srt subtitle files (detection implemented, rendering would require custom implementation)
- Handles screen rotation via `android:configChanges` in manifest
- Keeps screen on during playback

## Supported Video Formats
- .mp4
- .mkv
- .avi
- .mov

## Permission Handling
- Android 13+ (API 33+): `READ_MEDIA_VIDEO` permission
- Android 12 and below: `READ_EXTERNAL_STORAGE` permission
- Graceful permission request flow with UI feedback

## Technical Decisions

### Why VideoView?
- Lightweight and part of core Android framework
- No external dependencies required
- Sufficient for basic playback needs
- Keeps APK size minimal

### Why MediaStore?
- Standard Android API for media queries
- Efficient for scanning large storage
- Provides metadata like file name and duration
- Works across different Android versions

### Gesture Implementation
- Uses `GestureDetector.SimpleOnGestureListener`
- Screen divided into three zones for double-tap detection
- Supports both swipe and double-tap for seeking
- Responsive and intuitive user experience

## Build Configuration
- **minSdk**: 21 (Android 5.0) - Broad device compatibility
- **targetSdk**: 33 (Android 13) - Modern Android features
- **Kotlin**: 1.8.0 - Stable and widely supported
- **Android Gradle Plugin**: 7.4.2 - Stable build tools

## CI/CD Pipeline
GitHub Actions workflow:
1. Builds release APK
2. Generates demo keystore for signing
3. Signs APK with demo credentials
4. Uploads artifact
5. Creates GitHub release (on main/master branch)

## Future Enhancements
- Subtitle rendering (requires custom view or ExoPlayer)
- Video playback controls overlay
- Playlist support
- Video thumbnails in list
- Recently played tracking
- Video quality selection
- Playback speed control
