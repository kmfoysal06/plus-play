# Plus Play - Lightweight Android Video Player

A minimal, lightweight Android video player app written in Kotlin that provides essential video playback features.

## Features

- **Video File Listing**: Scans and displays video files from device storage (.mp4, .mkv, .avi, .mov)
- **Folder Navigation**: Browse videos organized by folders with instant loading (no lag)
- **Intuitive Gestures**:
  - Swipe left/right: Seek 10 seconds backward/forward (responsive 100px threshold)
  - Double-tap left/right: Seek 10 seconds backward/forward
  - Double-tap middle: Play/Pause
  - Single tap: Toggle system UI
- **Playback Resume**: Automatically remembers playback position for last 3 videos
  - Resume dialog offers "Resume" or "Start from Beginning" options
  - Handles Bluetooth disconnect and system interruptions gracefully
- **Subtitle Support**: Automatically loads .srt subtitle files with matching basename
- **Full-Screen Mode**: Immersive video playback experience
- **Rotation Support**: Handles device orientation changes
- **Storage Permissions**: Proper handling for Android 13+ and legacy versions
- **Minimal UI**: Clean, simple interface with two activities (list and player)

## Requirements

- Android 5.0 (API level 21) or higher
- Storage permissions for accessing video files

## Building

### Using Android Studio
1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Build and run on your device or emulator

### Using Command Line
```bash
./gradlew assembleDebug
```

### GitHub Actions
The project includes a GitHub Actions workflow that automatically:
- Builds release APK
- Signs it with a demo keystore
- Creates a release with the signed APK

## Architecture

The app uses core Android APIs only:
- `VideoView` for video playback
- `MediaStore` for scanning video files
- `RecyclerView` for displaying video list
- `GestureDetector` for handling touch gestures

## Project Structure

```
app/
├── src/main/
│   ├── java/com/plusplay/app/
│   │   ├── MainActivity.kt          # Video list activity
│   │   ├── PlayerActivity.kt        # Video player with gestures
│   │   ├── VideoAdapter.kt          # RecyclerView adapter
│   │   └── VideoFile.kt             # Data model
│   ├── res/
│   │   ├── layout/                  # UI layouts
│   │   ├── values/                  # Strings, colors, styles
│   │   └── drawable/                # App icon
│   └── AndroidManifest.xml
```

## Usage

1. Launch the app
2. Grant storage permission when prompted
3. Select a video from the list
4. Use gestures to control playback:
   - **Swipe or double-tap sides**: Seek forward/backward
   - **Double-tap center**: Play/Pause
   - **Single tap**: Show/hide controls

## License

This project is open source and available under the MIT License.