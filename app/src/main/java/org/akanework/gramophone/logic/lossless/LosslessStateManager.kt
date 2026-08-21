package org.akanework.gramophone.logic.lossless

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Глобальный реактивный менеджер FLAC-статусов.
 * Хранит подтвержденные Lossless/FLAC ID треков, мгновенно обновляя UI во всем приложении.
 */
object LosslessStateManager {

    private const val PREFS_NAME = "SalvationLosslessState"
    private const val KEY_LOSSLESS_IDS = "confirmed_lossless_ids"

    private val _losslessTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val losslessTrackIds: StateFlow<Set<String>> = _losslessTrackIds.asStateFlow()

    private val inMemorySet = ConcurrentHashMap.newKeySet<String>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_LOSSLESS_IDS, emptySet()) ?: emptySet()
        inMemorySet.addAll(saved)
        _losslessTrackIds.value = inMemorySet.toSet()

        val savedAlbums = prefs.getStringSet("lossless_album_ids", emptySet()) ?: emptySet()
        inMemoryAlbumSet.addAll(savedAlbums)

        val savedPlaylists = prefs.getStringSet("lossless_playlist_ids", emptySet()) ?: emptySet()
        inMemoryPlaylistSet.addAll(savedPlaylists.mapNotNull { it.toIntOrNull() })
    }

    fun markLossless(context: Context?, trackId: String?) {
        if (trackId.isNullOrBlank()) return
        if (inMemorySet.add(trackId)) {
            _losslessTrackIds.value = inMemorySet.toSet()
            context?.let { saveToPrefs(it) }
        }
    }

    fun markLossless(context: Context?, trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        var changed = false
        for (id in trackIds) {
            if (id.isNotBlank() && inMemorySet.add(id)) {
                changed = true
            }
        }
        if (changed) {
            _losslessTrackIds.value = inMemorySet.toSet()
            context?.let { saveToPrefs(it) }
        }
    }

    fun isTrackLossless(trackId: String?, default: Boolean = false): Boolean {
        if (default) return true
        if (trackId.isNullOrBlank()) return false
        return inMemorySet.contains(trackId)
    }

    @Composable
    fun rememberIsTrackLossless(trackId: String?, default: Boolean = false): Boolean {
        if (default) return true
        if (trackId.isNullOrBlank()) return false
        val state by losslessTrackIds.collectAsState()
        return state.contains(trackId)
    }

    private val inMemoryAlbumSet = ConcurrentHashMap.newKeySet<String>()
    private val inMemoryPlaylistSet = ConcurrentHashMap.newKeySet<Int>()

    fun markAlbumLossless(context: Context?, albumId: String?) {
        if (albumId.isNullOrBlank()) return
        if (inMemoryAlbumSet.add(albumId)) {
            context?.let { saveToPrefs(it) }
        }
    }

    fun isAlbumLossless(albumId: String?, default: Boolean = false): Boolean {
        if (default) return true
        if (albumId.isNullOrBlank()) return false
        return inMemoryAlbumSet.contains(albumId)
    }

    fun markPlaylistLossless(context: Context?, playlistId: Int?) {
        if (playlistId == null || playlistId <= 0) return
        if (inMemoryPlaylistSet.add(playlistId)) {
            context?.let { saveToPrefs(it) }
        }
    }

    fun isPlaylistLossless(playlistId: Int?, default: Boolean = false): Boolean {
        if (default) return true
        if (playlistId == null || playlistId <= 0) return false
        return inMemoryPlaylistSet.contains(playlistId)
    }

    private fun saveToPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putStringSet(KEY_LOSSLESS_IDS, inMemorySet.toSet())
                .putStringSet("lossless_album_ids", inMemoryAlbumSet.toSet())
                .putStringSet("lossless_playlist_ids", inMemoryPlaylistSet.map { it.toString() }.toSet())
                .apply()
        } catch (_: Exception) {}
    }
}
