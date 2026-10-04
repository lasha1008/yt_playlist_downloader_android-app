# YouTube Playlist & Video Downloader for Android

A modern Android application built with Jetpack Compose and `youtubedl-android` / `ffmpeg` for downloading YouTube videos and playlists.

## Features

- Download individual videos or entire playlists.
- Powered by `youtubedl-android` and `ffmpeg`.
- Built with modern Jetpack Compose Material 3 UI.
- Background downloads and notification support.

## Download & Install

Head over to the [**Releases**](https://github.com/lasha1008/yt_playlist_downloader_android-app/releases) section to download the latest APK:

- **`app-universal-release.apk`**: Works on all Android devices.
- **`app-arm64-v8a-release.apk`**: Optimized for modern 64-bit Android smartphones (smaller file size, recommended).
- **`app-armeabi-v7a-release.apk`**: For older 32-bit Android devices.

> **Note:** When installing the APK directly on Android, make sure to allow "Install unknown apps" for your browser/file manager if prompted.

## Building from Source

To build the APK locally:

```bash
git clone https://github.com/lasha1008/yt_playlist_downloader_android-app.git
cd yt_playlist_downloader_android-app
./gradlew assembleRelease
```

The APKs will be generated in `app/build/outputs/apk/release/`.
