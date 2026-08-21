package org.akanework.gramophone.logic.lossless

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.AlbumFlacStatusResponse
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.PlaylistFlacStatusResponse
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.MainActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object FlacDownloadManager {

    private const val CHANNEL_ID = "flac_downloads"
    private const val CHANNEL_NAME = "Загрузка FLAC"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _albumStatuses = MutableStateFlow<Map<String, AlbumFlacStatusResponse>>(emptyMap())
    val albumStatuses: StateFlow<Map<String, AlbumFlacStatusResponse>> = _albumStatuses.asStateFlow()

    private val _playlistStatuses = MutableStateFlow<Map<Int, PlaylistFlacStatusResponse>>(emptyMap())
    val playlistStatuses: StateFlow<Map<Int, PlaylistFlacStatusResponse>> = _playlistStatuses.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resumedActivitiesCount = AtomicInteger(0)

    fun init(context: Context) {
        LosslessStateManager.init(context)
        createNotificationChannel(context)

        val app = context.applicationContext as? Application
        app?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumedActivitiesCount.incrementAndGet()
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivitiesCount.decrementAndGet()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о скачивании треков и альбомов в Lossless качестве"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun isAppInForeground(): Boolean {
        return resumedActivitiesCount.get() > 0
    }

    private fun showFeedback(context: Context, title: String, message: String, notificationId: Int = (System.currentTimeMillis() % 10000).toInt()) {
        if (isAppInForeground()) {
            mainHandler.post {
                Toast.makeText(context.applicationContext, "$title: $message", Toast.LENGTH_SHORT).show()
            }
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            } catch (_: SecurityException) {}
        }
    }

    // ==========================================
    // СКАЧИВАНИЕ И ОПРОС АЛЬБОМОВ
    // ==========================================

    fun downloadAlbum(context: Context, albumId: String, albumTitle: String, tracks: List<Track>? = null) {
        val key = "album_$albumId"
        if (activeJobs[key]?.isActive == true) {
            showFeedback(context, "Загрузка FLAC", "Альбом уже скачивается")
            return
        }

        showFeedback(context, "Загрузка FLAC", "Скачивание альбома «$albumTitle» запущено")

        val job = scope.launch {
            try {
                val api = NetworkClient.getApi(context)
                api.downloadAlbumFlac(albumId).execute()
            } catch (_: Exception) {}

            pollAlbumStatus(context, albumId, albumTitle, tracks)
        }
        activeJobs[key] = job
    }

    fun pollAlbumStatus(context: Context, albumId: String, albumTitle: String, tracks: List<Track>? = null) {
        val key = "album_$albumId"
        activeJobs[key]?.cancel()

        val job = scope.launch {
            val api = NetworkClient.getApi(context)
            var attempts = 0
            while (attempts < 120) { // максимум 5 минут поллинга
                attempts++
                try {
                    val resp = api.getAlbumFlacStatus(albumId).execute()
                    if (resp.isSuccessful && resp.body() != null) {
                        val status = resp.body()!!
                        val currentMap = _albumStatuses.value.toMutableMap()
                        currentMap[albumId] = status
                        _albumStatuses.value = currentMap

                        if (status.hasFlac || status.isComplete || status.percent >= 100f || (status.totalTracks > 0 && status.flacTracks >= status.totalTracks)) {
                            tracks?.let {
                                LosslessStateManager.markLossless(context, it.map { t -> t.id })
                            }
                            showFeedback(
                                context,
                                "Lossless FLAC готов",
                                "Альбом «$albumTitle» успешно скачан (${status.flacTracks}/${status.totalTracks} треков)"
                            )
                            break
                        }
                    }
                } catch (_: Exception) {}
                delay(2500)
            }
            activeJobs.remove(key)
        }
        activeJobs[key] = job
    }

    fun fetchAlbumStatus(context: Context, albumId: String, tracks: List<Track>? = null) {
        scope.launch {
            try {
                val resp = NetworkClient.getApi(context).getAlbumFlacStatus(albumId).execute()
                if (resp.isSuccessful && resp.body() != null) {
                    val status = resp.body()!!
                    val currentMap = _albumStatuses.value.toMutableMap()
                    currentMap[albumId] = status
                    _albumStatuses.value = currentMap

                    if (status.hasFlac || status.percent >= 100f || (status.totalTracks > 0 && status.flacTracks >= status.totalTracks)) {
                        tracks?.let {
                            LosslessStateManager.markLossless(context, it.map { t -> t.id })
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun fetchPlaylistStatus(context: Context, playlistId: Int, tracks: List<Track>? = null) {
        scope.launch {
            try {
                val resp = NetworkClient.getApi(context).getPlaylistFlacStatus(playlistId).execute()
                if (resp.isSuccessful && resp.body() != null) {
                    val status = resp.body()!!
                    val currentMap = _playlistStatuses.value.toMutableMap()
                    currentMap[playlistId] = status
                    _playlistStatuses.value = currentMap

                    if (status.isComplete || status.percent >= 100f || (status.totalTracks > 0 && status.flacTracks >= status.totalTracks)) {
                        tracks?.let {
                            LosslessStateManager.markLossless(context, it.map { t -> t.id })
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ==========================================
    // СКАЧИВАНИЕ И ОПРОС ПЛЕЙЛИСТОВ
    // ==========================================

    fun downloadPlaylist(context: Context, playlistId: Int, playlistTitle: String, tracks: List<Track>? = null) {
        val key = "playlist_$playlistId"
        if (activeJobs[key]?.isActive == true) {
            showFeedback(context, "Загрузка FLAC", "Плейлист уже скачивается")
            return
        }

        showFeedback(context, "Загрузка FLAC", "Скачивание плейлиста «$playlistTitle» запущено")

        val job = scope.launch {
            try {
                val api = NetworkClient.getApi(context)
                api.downloadPlaylistFlac(playlistId).execute()
            } catch (_: Exception) {}

            pollPlaylistStatus(context, playlistId, playlistTitle, tracks)
        }
        activeJobs[key] = job
    }

    fun pollPlaylistStatus(context: Context, playlistId: Int, playlistTitle: String, tracks: List<Track>? = null) {
        val key = "playlist_$playlistId"
        activeJobs[key]?.cancel()

        val job = scope.launch {
            val api = NetworkClient.getApi(context)
            var attempts = 0
            while (attempts < 150) {
                attempts++
                try {
                    val resp = api.getPlaylistFlacStatus(playlistId).execute()
                    if (resp.isSuccessful && resp.body() != null) {
                        val status = resp.body()!!
                        val currentMap = _playlistStatuses.value.toMutableMap()
                        currentMap[playlistId] = status
                        _playlistStatuses.value = currentMap

                        if (status.isComplete || status.percent >= 100f || (status.totalTracks > 0 && status.flacTracks >= status.totalTracks)) {
                            tracks?.let {
                                LosslessStateManager.markLossless(context, it.map { t -> t.id })
                            }
                            showFeedback(
                                context,
                                "Lossless FLAC готов",
                                "Плейлист «$playlistTitle» успешно скачан (${status.flacTracks}/${status.totalTracks} треков)"
                            )
                            break
                        }
                    }
                } catch (_: Exception) {}
                delay(2500)
            }
            activeJobs.remove(key)
        }
        activeJobs[key] = job
    }

    // ==========================================
    // СКАЧИВАНИЕ И ОПРОС ОТДЕЛЬНОГО ТРЕКА
    // ==========================================

    fun downloadTrack(context: Context, trackId: String, trackTitle: String) {
        val key = "track_$trackId"
        if (activeJobs[key]?.isActive == true) {
            showFeedback(context, "Загрузка FLAC", "Трек уже скачивается")
            return
        }

        showFeedback(context, "Загрузка FLAC", "«$trackTitle» поставлен в очередь на скачивание")

        val job = scope.launch {
            try {
                val api = NetworkClient.getApi(context)
                api.downloadTrackFlac(trackId).execute()
            } catch (_: Exception) {}

            var attempts = 0
            while (attempts < 60) {
                attempts++
                delay(2500)
                try {
                    val resp = NetworkClient.getApi(context).getTrackResolveInfo(trackId).execute()
                    if (resp.isSuccessful && resp.body() != null) {
                        val info = resp.body()!!
                        if (info.isLossless || info.hasLocalFlac) {
                            LosslessStateManager.markLossless(context, trackId)
                            showFeedback(context, "FLAC готов", "«$trackTitle» успешно скачан в Lossless качестве")
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
            activeJobs.remove(key)
        }
        activeJobs[key] = job
    }

    fun isAlbumDownloading(albumId: String): Boolean = activeJobs.containsKey("album_$albumId")
    fun isPlaylistDownloading(playlistId: Int): Boolean = activeJobs.containsKey("playlist_$playlistId")
    fun isTrackDownloading(trackId: String): Boolean = activeJobs.containsKey("track_$trackId")
}
