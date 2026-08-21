package org.akanework.gramophone.logic.offline

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object OfflineStateManager {

    private val downloadedTrackIds = ConcurrentHashMap.newKeySet<String>()
    private val _downloadedTracksState = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTracksState: StateFlow<Set<String>> = _downloadedTracksState.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = OfflineMusicDatabase.getInstance(context)
                val ids = db.getAllDownloadedTrackIds()
                downloadedTrackIds.clear()
                downloadedTrackIds.addAll(ids)
                _downloadedTracksState.value = downloadedTrackIds.toSet()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isDownloaded(trackId: String): Boolean {
        return downloadedTrackIds.contains(trackId)
    }

    fun markDownloaded(trackId: String) {
        downloadedTrackIds.add(trackId)
        _downloadedTracksState.value = downloadedTrackIds.toSet()
    }

    fun markRemoved(trackId: String) {
        downloadedTrackIds.remove(trackId)
        _downloadedTracksState.value = downloadedTrackIds.toSet()
    }

    @Composable
    fun rememberIsTrackDownloaded(trackId: String): Boolean {
        val allDownloaded by downloadedTracksState.collectAsState()
        return remember(trackId, allDownloaded) {
            allDownloaded.contains(trackId)
        }
    }
}
