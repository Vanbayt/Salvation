package org.akanework.gramophone.logic.offline

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.logic.utils.PlaybackLogger
import org.akanework.gramophone.logic.utils.exoplayer.AuthenticatedDataSourceFactory
import org.akanework.gramophone.ui.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class TrackDownloadState {
    IDLE, QUEUED, DOWNLOADING, COMPLETED, FAILED
}

data class LibraryDownloadStatus(
    val totalTracks: Int = 0,
    val downloadedTracks: Int = 0,
    val percent: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val estimatedSecondsRemaining: Int = -1,
    val tracksPerMinute: Float = 0f
)

object OfflineDownloadManager {

    private const val TAG = "OfflineDownloadManager"
    private const val CHANNEL_ID = "salvation_offline_downloads"
    private const val CHANNEL_NAME = "Оффлайн загрузки"
    private const val NOTIFY_ID = 8844
    private const val PARALLEL_DOWNLOADS = 3

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    private val _libraryStatus = MutableStateFlow(LibraryDownloadStatus())
    val libraryStatus: StateFlow<LibraryDownloadStatus> = _libraryStatus.asStateFlow()

    private val _libraryTracks = MutableStateFlow<List<Track>>(emptyList())
    val libraryTracks: StateFlow<List<Track>> = _libraryTracks.asStateFlow()

    private val _trackStates = MutableStateFlow<Map<String, TrackDownloadState>>(emptyMap())
    val trackStates: StateFlow<Map<String, TrackDownloadState>> = _trackStates.asStateFlow()

    private val trackStatesMap = ConcurrentHashMap<String, TrackDownloadState>()

    private val downloadHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build()

    private val resumedActivitiesCount = AtomicInteger(0)

    fun init(context: Context) {
        OfflineStateManager.init(context)
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Прогресс скачивания треков для оффлайн прослушивания"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun isAppInForeground(): Boolean = resumedActivitiesCount.get() > 0

    fun isDownloading(): Boolean = _libraryStatus.value.isDownloading

    fun downloadLibrary(context: Context) {
        if (_libraryStatus.value.isDownloading) return

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val db = OfflineMusicDatabase.getInstance(context)
            val allTracks = fetchFullLibrary(context)

            if (allTracks.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Медиатека пуста или нет сети", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            _libraryTracks.value = allTracks

            val pendingTracks = allTracks.filter { !db.isTrackDownloaded(it.id) }
            val alreadyDownloaded = allTracks.size - pendingTracks.size

            if (pendingTracks.isEmpty()) {
                _libraryStatus.value = LibraryDownloadStatus(
                    totalTracks = allTracks.size,
                    downloadedTracks = allTracks.size,
                    percent = 100f,
                    isDownloading = false,
                    isComplete = true,
                    estimatedSecondsRemaining = 0,
                    tracksPerMinute = 0f
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Вся медиатека уже скачана на устройство", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Скачивание ${pendingTracks.size} треков...", Toast.LENGTH_SHORT).show()
            }

            // Инициализация статусов
            allTracks.forEach { track ->
                trackStatesMap[track.id] = if (db.isTrackDownloaded(track.id)) {
                    TrackDownloadState.COMPLETED
                } else {
                    TrackDownloadState.QUEUED
                }
            }
            _trackStates.value = trackStatesMap.toMap()

            val total = allTracks.size
            val completedCount = AtomicInteger(alreadyDownloaded)
            val startTimeMs = System.currentTimeMillis()
            val initialDownloaded = alreadyDownloaded

            fun updateLibraryProgress() {
                val done = completedCount.get()
                val percent = if (total > 0) (done.toFloat() / total.toFloat()) * 100f else 0f
                val isComplete = done >= total
                val newlyDone = (done - initialDownloaded).coerceAtLeast(0)
                val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
                val remaining = (total - done).coerceAtLeast(0)

                val (etaSec, speedTpm) = if (newlyDone > 0 && elapsedSec > 1.0) {
                    val tracksPerSec = newlyDone / elapsedSec
                    val eta = (remaining / tracksPerSec).toInt()
                    val tpm = (tracksPerSec * 60.0).toFloat()
                    Pair(eta, tpm)
                } else if (remaining > 0) {
                    Pair((remaining * 0.8).toInt(), 75f)
                } else {
                    Pair(0, 0f)
                }

                _libraryStatus.value = LibraryDownloadStatus(
                    totalTracks = total,
                    downloadedTracks = done,
                    percent = percent.coerceIn(0f, 100f),
                    isDownloading = !isComplete,
                    isComplete = isComplete,
                    estimatedSecondsRemaining = if (isComplete) 0 else etaSec,
                    tracksPerMinute = if (isComplete) 0f else speedTpm
                )
                _trackStates.value = trackStatesMap.toMap()
                updateNotification(context, done, total, etaSec)
            }

            updateLibraryProgress()

            val semaphore = Semaphore(PARALLEL_DOWNLOADS)
            val downloadJobs = pendingTracks.map { track ->
                launch {
                    semaphore.withPermit {
                        trackStatesMap[track.id] = TrackDownloadState.DOWNLOADING
                        _trackStates.value = trackStatesMap.toMap()

                        val success = downloadSingleTrack(context, track)
                        if (success) {
                            trackStatesMap[track.id] = TrackDownloadState.COMPLETED
                            completedCount.incrementAndGet()
                        } else {
                            trackStatesMap[track.id] = TrackDownloadState.FAILED
                        }
                        updateLibraryProgress()
                    }
                }
            }

            downloadJobs.forEach { it.join() }

            val finalDone = completedCount.get()
            _libraryStatus.value = LibraryDownloadStatus(
                totalTracks = total,
                downloadedTracks = finalDone,
                percent = if (total > 0) (finalDone.toFloat() / total.toFloat()) * 100f else 100f,
                isDownloading = false,
                isComplete = finalDone >= total,
                estimatedSecondsRemaining = 0,
                tracksPerMinute = 0f
            )
            _trackStates.value = trackStatesMap.toMap()

            dismissNotification(context)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Скачивание завершено: $finalDone из $total треков", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun downloadSingleTrack(context: Context, track: Track): Boolean = withContext(Dispatchers.IO) {
        val baseDir = File(context.filesDir, "salvation_downloads")
        val audioDir = File(baseDir, "audio").apply { if (!exists()) mkdirs() }
        val coversDir = File(baseDir, "covers").apply { if (!exists()) mkdirs() }
        val db = OfflineMusicDatabase.getInstance(context)

        try {
            // 🔥 ИСПОЛЬЗУЕМ ТОТ ЖЕ САМЫЙ AuthenticatedDataSource ИЗ СТЕКА ВОСПРОИЗВЕДЕНИЯ ПЛЕЕРА
            val rawUri = Uri.parse("http://185.196.41.31/stream/${track.id}")
            val dataSpec = DataSpec.Builder()
                .setUri(rawUri)
                .build()

            val dataSource = AuthenticatedDataSourceFactory(context).createDataSource()
            val tempAudioFile = File(audioDir, "${track.id}.tmp")
            if (tempAudioFile.exists()) tempAudioFile.delete()

            var success = false

            try {
                dataSource.open(dataSpec)
                FileOutputStream(tempAudioFile, false).use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (true) {
                        bytesRead = dataSource.read(buffer, 0, buffer.size)
                        if (bytesRead <= 0 || bytesRead == C.RESULT_END_OF_INPUT) break
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
                if (tempAudioFile.exists() && tempAudioFile.length() > 50000L) {
                    success = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "AuthenticatedDataSource download error for track ${track.id}: ${e.message}")
            } finally {
                try {
                    dataSource.close()
                } catch (_: Exception) {}
            }

            val isLossless = track.is_lossless
            val ext = if (isLossless) "flac" else "m4a"
            val audioFile = File(audioDir, "${track.id}.$ext")

            if (success && tempAudioFile.exists() && tempAudioFile.length() > 50000L) {
                if (audioFile.exists()) audioFile.delete()
                tempAudioFile.renameTo(audioFile)
            } else {
                tempAudioFile.delete()
                PlaybackLogger.log("DOWNLOAD_ERR", "Track ${track.id} (${track.title}) failed to download")
                return@withContext false
            }

            // Сохранение обложки (Асинхронно)
            var localCoverPath: String? = null
            if (!track.cover.isNullOrBlank()) {
                val fullCoverUrl = if (track.cover.startsWith("/")) "http://185.196.41.31${track.cover}" else track.cover
                val coverFile = File(coversDir, "${track.id}.jpg")
                try {
                    val coverReq = Request.Builder().url(fullCoverUrl).build()
                    downloadHttpClient.newCall(coverReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { input ->
                                FileOutputStream(coverFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (coverFile.exists()) {
                                localCoverPath = coverFile.absolutePath
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Запись в SQLite
            val record = OfflineTrackRecord(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.duration.toLong() * 1000L,
                filePath = audioFile.absolutePath,
                coverPath = localCoverPath,
                format = ext.uppercase(),
                fileSize = audioFile.length(),
                downloadedAt = System.currentTimeMillis(),
                isLossless = isLossless
            )
            db.insertTrack(record)
            OfflineStateManager.markDownloaded(track.id)
            PlaybackLogger.log("DOWNLOAD_DONE", "⚡ [OFFLINE_SAVED] Track ${track.id} (${track.title}) downloaded via AuthenticatedDataSource")
            return@withContext true
        } catch (e: Exception) {
            PlaybackLogger.log("DOWNLOAD_EXC", "Exception on track ${track.id}: ${e.message}")
            return@withContext false
        }
    }

    private suspend fun fetchFullLibrary(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val allList = mutableListOf<Track>()
        try {
            val api = NetworkClient.getApi(context)
            var skip = 0
            val limit = 200
            while (true) {
                val resp = api.getFavorites(skip = skip, limit = limit, sortMode = "newest").execute()
                if (!resp.isSuccessful) break
                val body = resp.body()
                if (body.isNullOrEmpty()) break
                allList.addAll(body)
                if (body.size < limit) break
                skip += body.size
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext allList
    }

    private fun formatEtaText(seconds: Int): String {
        if (seconds <= 0) return ""
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins >= 60) {
            val hours = mins / 60
            val remMins = mins % 60
            " • ~$hours ч. $remMins мин."
        } else if (mins > 0) {
            " • ~$mins мин."
        } else {
            " • ~$secs сек."
        }
    }

    private fun updateNotification(context: Context, done: Int, total: Int, etaSec: Int = -1) {
        if (isAppInForeground()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val etaStr = formatEtaText(etaSec)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Скачивание медиатеки")
            .setContentText("$done из $total треков готово$etaStr")
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
        } catch (_: SecurityException) {}
    }

    private fun dismissNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
        } catch (_: Exception) {}
    }
}
