package com.example.ytdownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class PlaylistItem(val index: Int, val title: String, val thumbnailUrl: String? = null, val spotifySearchQuery: String? = null)

sealed class DownloadState {
    object Idle : DownloadState()
    object FetchingInfo : DownloadState()
    data class PlaylistSelection(val title: String, val url: String, val items: List<PlaylistItem>, val isAudio: Boolean, val isSpotify: Boolean = false) : DownloadState()
    data class Downloading(val progress: Float, val eta: String, val speed: String, val currentItem: String) : DownloadState()
    object Processing : DownloadState()
    data class Completed(val message: String, val isAudio: Boolean) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var isYoutubeDlUpdated = false

    fun fetchInfo(url: String, isAudio: Boolean) {
        viewModelScope.launch {
            _state.value = DownloadState.FetchingInfo
            withContext(Dispatchers.IO) {
                try {
                    if (url.contains("spotify.com")) {
                        val embedUrl = url.replace("open.spotify.com", "open.spotify.com/embed")
                        val connection = java.net.URL(embedUrl).openConnection() as java.net.HttpURLConnection
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                        val html = connection.inputStream.bufferedReader().use { it.readText() }
                        
                        val match = "id=\"__NEXT_DATA__\" type=\"application/json\">([^<]+)</script>".toRegex().find(html)
                        if (match != null) {
                            val json = match.groupValues[1]
                            val data = org.json.JSONObject(json)
                            val entity = data.getJSONObject("props").getJSONObject("pageProps")
                                .getJSONObject("state").getJSONObject("data").getJSONObject("entity")
                            
                            val playlistTitle = entity.optString("title", "Spotify Selection")
                            val playlistItems = mutableListOf<PlaylistItem>()
                            
                            val trackList = entity.optJSONArray("trackList")
                            if (trackList != null) {
                                for (i in 0 until trackList.length()) {
                                    val t = trackList.getJSONObject(i)
                                    val title = t.optString("title", "Unknown")
                                    val subtitle = t.optString("subtitle", "")
                                    val coverArt = t.optJSONObject("coverArt")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url", null)
                                    
                                    playlistItems.add(PlaylistItem(i + 1, "$title - $subtitle", coverArt, "ytsearch1:\"$title $subtitle\""))
                                }
                                _state.value = DownloadState.PlaylistSelection(
                                    title = playlistTitle,
                                    url = url,
                                    items = playlistItems,
                                    isAudio = isAudio,
                                    isSpotify = true
                                )
                                return@withContext
                            }
                        }
                        _state.value = DownloadState.Error("Failed to parse Spotify metadata")
                        return@withContext
                    }

                    val context = getApplication<Application>()
                    YoutubeDL.getInstance().init(context)
                    
                    val request = YoutubeDLRequest(url)
                    request.addOption("--flat-playlist")
                    request.addOption("--dump-single-json")
                    
                    val response = YoutubeDL.getInstance().execute(request, "info_task", null)
                    val jsonOutput = response.out
                    
                    if (jsonOutput != null && jsonOutput.isNotBlank()) {
                        val jsonObject = org.json.JSONObject(jsonOutput)
                        if (jsonObject.has("entries")) {
                            val entriesArray = jsonObject.getJSONArray("entries")
                            if (entriesArray.length() > 1) {
                                val playlistItems = mutableListOf<PlaylistItem>()
                                for (i in 0 until entriesArray.length()) {
                                    val entry = entriesArray.getJSONObject(i)
                                    val title = entry.optString("title", "Unknown Video ${i + 1}")
                                    
                                    var thumbnailUrl: String? = null
                                    if (entry.has("thumbnails")) {
                                        val thumbnails = entry.getJSONArray("thumbnails")
                                        if (thumbnails.length() > 0) {
                                            thumbnailUrl = thumbnails.getJSONObject(0).optString("url", null)
                                        }
                                    }
                                    if (thumbnailUrl == null && entry.has("thumbnail")) {
                                        thumbnailUrl = entry.optString("thumbnail", null)
                                    }
                                    
                                    playlistItems.add(PlaylistItem(i + 1, title, thumbnailUrl))
                                }
                                val playlistTitle = jsonObject.optString("title", "Playlist")
                                _state.value = DownloadState.PlaylistSelection(
                                    title = playlistTitle,
                                    url = url,
                                    items = playlistItems,
                                    isAudio = isAudio
                                )
                            } else {
                                startDownload(url, isAudio, null)
                            }
                        } else {
                            startDownload(url, isAudio, null)
                        }
                    } else {
                        startDownload(url, isAudio, null)
                    }
                } catch (e: Exception) {
                    _state.value = DownloadState.Error("Failed to fetch info: ${e.message}")
                }
            }
        }
    }

    fun startDownload(url: String, isAudio: Boolean, selectedIndices: List<Int>? = null, spotifyQueries: List<String>? = null) {
        viewModelScope.launch {
            _state.value = DownloadState.FetchingInfo
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "download_channel"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
                    notificationManager.createNotificationChannel(channel)
                }
                val notificationId = 1001
                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Initializing Download...")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                
                notificationManager.notify(notificationId, builder.build())

                try {
                    // Initialize on-demand if not already initialized
                    try {
                        YoutubeDL.getInstance().init(context)
                        FFmpeg.getInstance().init(context)
                        
                        if (!isYoutubeDlUpdated) {
                            _state.value = DownloadState.Downloading(0f, "Updating Engine", "Wait...", "Updating yt-dlp to latest version...")
                            builder.setContentTitle("Updating Engine...")
                            notificationManager.notify(notificationId, builder.build())
                            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                            isYoutubeDlUpdated = true
                        }
                    } catch (e: Exception) {
                        _state.value = DownloadState.Error("Init/Update failed: ${e.message}")
                        builder.setContentTitle("Failed").setContentText(e.message).setOngoing(false).setSmallIcon(android.R.drawable.stat_notify_error)
                        notificationManager.notify(notificationId, builder.build())
                        return@withContext
                    }

                    val workDir = File(context.cacheDir, "ytdl_tmp")
                    if (!workDir.exists()) workDir.mkdirs()

                    // Clear previous cache if any
                    workDir.listFiles()?.forEach { it.delete() }

                    // Query MediaStore to avoid re-downloading
                    val resolver = context.contentResolver
                    val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
                    val collection = if (isAudio) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        }
                    }

                    val existingFiles = mutableSetOf<String>()
                    try {
                        resolver.query(collection, projection, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                            while (cursor.moveToNext()) {
                                val name = cursor.getString(nameIndex)
                                if (name != null) existingFiles.add(name)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadViewModel", "Failed to query MediaStore", e)
                    }

                    for (name in existingFiles) {
                        try {
                            File(workDir, name).createNewFile()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }

                    val request: YoutubeDLRequest
                    if (spotifyQueries != null && spotifyQueries.isNotEmpty()) {
                        val batchFile = File(workDir, "batch.txt")
                        batchFile.writeText(spotifyQueries.joinToString("\n"))
                        request = YoutubeDLRequest(emptyList<String>())
                        request.addOption("--batch-file", batchFile.absolutePath)
                    } else {
                        request = YoutubeDLRequest(url)
                        if (selectedIndices != null && selectedIndices.isNotEmpty()) {
                            val itemsString = selectedIndices.joinToString(",")
                            request.addOption("--playlist-items", itemsString)
                        }
                    }

                    request.addOption("--no-mtime")
                    request.addOption("--no-update")
                    request.addOption("--ignore-errors")
                    request.addOption("--force-ipv4")
                    request.addOption("--no-overwrites")
                    
                    if (selectedIndices != null && selectedIndices.isNotEmpty()) {
                        val itemsString = selectedIndices.joinToString(",")
                        request.addOption("--playlist-items", itemsString)
                    }

                    if (isAudio) {
                        request.addOption("--extract-audio")
                        request.addOption("--audio-format", "mp3")
                        request.addOption("--embed-metadata")
                        request.addOption("--embed-thumbnail")
                        request.addOption("-o", File(workDir, "%(title)s.%(ext)s").absolutePath)
                    } else {
                        request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                        request.addOption("-o", File(workDir, "%(title)s.%(ext)s").absolutePath)
                    }

                    _state.value = DownloadState.Downloading(0f, "Calculating...", "0 KB/s", "Starting...")

                    var playlistStatus = ""
                    var currentTitle = ""
                    var currentSpeed = "0 KB/s"
                    var downloadException: Exception? = null
                    var lastNotificationTime = 0L
                    var skippedCount = 0
                    try {
                        YoutubeDL.getInstance().execute(request, "download_task") { progress, etaInSeconds, line ->
                            if (line.contains("has already been downloaded")) {
                                skippedCount++
                            }

                            val playlistMatch = "\\[download\\] Downloading (?:video|item) (\\d+ of \\d+)".toRegex().find(line)
                            if (playlistMatch != null) {
                                playlistStatus = "Video ${playlistMatch.groupValues[1]}"
                            }

                            val destMatch = "\\[download\\] Destination: (.*)".toRegex().find(line)
                            if (destMatch != null) {
                                val filename = File(destMatch.groupValues[1]).name
                                currentTitle = filename
                            }

                            val mergeMatch = "\\[ffmpeg\\] Merging formats into \"(.*)\"".toRegex().find(line)
                            if (mergeMatch != null) {
                                val filename = File(mergeMatch.groupValues[1]).name
                                currentTitle = filename
                            }
                            
                            val extMatch = "\\[ExtractAudio\\] Destination: (.*)".toRegex().find(line)
                            if (extMatch != null) {
                                val filename = File(extMatch.groupValues[1]).name
                                currentTitle = filename
                            }

                            val speedMatch = "at\\s+([0-9.]+[a-zA-Z]+/s)".toRegex().find(line)
                            if (speedMatch != null) {
                                currentSpeed = speedMatch.groupValues[1]
                            }

                            val displayItem = if (playlistStatus.isNotEmpty()) {
                                if (currentTitle.isNotEmpty()) "$playlistStatus: $currentTitle" else playlistStatus
                            } else {
                                if (currentTitle.isNotEmpty()) currentTitle else "Downloading..."
                            }

                            _state.value = DownloadState.Downloading(
                                progress = progress,
                                eta = "${etaInSeconds}s",
                                speed = currentSpeed,
                                currentItem = displayItem
                            )
                            
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastNotificationTime > 1000) {
                                builder.setContentTitle(displayItem)
                                    .setContentText("Progress: ${String.format("%.1f", progress)}% | $currentSpeed | ETA: ${etaInSeconds}s")
                                    .setProgress(100, progress.toInt(), false)
                                notificationManager.notify(notificationId, builder.build())
                                lastNotificationTime = currentTime
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("DownloadViewModel", "yt-dlp finished with an error, but some files might have succeeded.", e)
                        downloadException = e
                    }

                    _state.value = DownloadState.Processing
                    builder.setContentTitle("Processing...")
                        .setContentText("Moving files to storage")
                        .setProgress(0, 0, true)
                    notificationManager.notify(notificationId, builder.build())

                    // Move to MediaStore
                    val files = workDir.listFiles()
                    var newlyDownloadedCount = 0
                    if (files != null && files.isNotEmpty()) {
                        val validAudioExts = listOf("mp3", "m4a", "webm", "ogg")
                        val validVideoExts = listOf("mp4", "webm", "mkv", "avi", "mov", "flv")
                        val validExts = if (isAudio) validAudioExts else validVideoExts
                        for (file in files) {
                            val isMediaFile = validExts.any { file.name.endsWith(".$it", ignoreCase = true) }
                            if (file.length() > 0L && isMediaFile) {
                                moveToMediaStore(file, isAudio)
                                newlyDownloadedCount++
                            }
                        }
                        if (downloadException != null) {
                            val msg = "Downloaded $newlyDownloadedCount file(s) (with some errors). Skipped $skippedCount."
                            _state.value = DownloadState.Completed(msg, isAudio)
                            builder.setContentTitle("Download Finished").setContentText(msg).setOngoing(false).setProgress(0, 0, false).setSmallIcon(android.R.drawable.stat_sys_download_done)
                            notificationManager.notify(notificationId, builder.build())
                        } else {
                            val msg = "Downloaded $newlyDownloadedCount file(s). Skipped $skippedCount."
                            _state.value = DownloadState.Completed(msg, isAudio)
                            builder.setContentTitle("Download Finished").setContentText(msg).setOngoing(false).setProgress(0, 0, false).setSmallIcon(android.R.drawable.stat_sys_download_done)
                            notificationManager.notify(notificationId, builder.build())
                        }
                    } else {
                        val msg = downloadException?.message ?: "No files found after download"
                        _state.value = DownloadState.Error(msg)
                        builder.setContentTitle("Download Error").setContentText(msg).setOngoing(false).setProgress(0, 0, false).setSmallIcon(android.R.drawable.stat_notify_error)
                        notificationManager.notify(notificationId, builder.build())
                    }
                    
                    // Cleanup
                    workDir.listFiles()?.forEach { it.delete() }

                } catch (e: Exception) {
                    Log.e("DownloadViewModel", "Download failed", e)
                    _state.value = DownloadState.Error(e.message ?: "Unknown error")
                    builder.setContentTitle("Download Failed").setContentText(e.message).setOngoing(false).setProgress(0, 0, false).setSmallIcon(android.R.drawable.stat_notify_error)
                    notificationManager.notify(notificationId, builder.build())
                }
            }
        }
    }

    private fun moveToMediaStore(file: File, isAudio: Boolean) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio) "audio/mpeg" else "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (isAudio) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        var uri: Uri? = null
        try {
            uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out!!)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadViewModel", "Failed to move file to MediaStore", e)
            uri?.let { resolver.delete(it, null, null) }
        }
    }

    fun reset() {
        _state.value = DownloadState.Idle
    }
}
