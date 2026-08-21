package org.akanework.gramophone.logic

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.Playlist
import org.akanework.gramophone.logic.api.Track
import java.io.File

object LibraryCacheManager {

    private val gson = Gson()
    private const val PREFS_NAME = "SalvationLibraryCache"
    private val memoryTracksCache = java.util.concurrent.ConcurrentHashMap<String, List<Track>>()

    fun getMemoryTracks(sortMode: String): List<Track>? = memoryTracksCache[sortMode]

    fun setMemoryTracks(sortMode: String, tracks: List<Track>) {
        memoryTracksCache[sortMode] = tracks
    }

    // ==========================================
    // UI КЭШ: ПЕРВЫЕ СТРАНИЦЫ (SharedPreferences)
    // ==========================================

    fun loadCachedTracks(context: Context, sortMode: String): List<Track> {
        val inMem = memoryTracksCache[sortMode]
        if (!inMem.isNullOrEmpty()) return inMem

        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("tracks_$sortMode", null) ?: return emptyList()
        val loaded = try { gson.fromJson<List<Track>>(json, object : TypeToken<List<Track>>() {}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
        if (loaded.isNotEmpty()) {
            memoryTracksCache[sortMode] = loaded
        }
        return loaded
    }

    fun saveCachedTracks(context: Context, sortMode: String, data: List<Track>) {
        memoryTracksCache[sortMode] = data
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("tracks_$sortMode", gson.toJson(data)).apply()
    }

    fun loadCachedAlbums(context: Context): List<Album> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("albums", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Album>>() {}.type) } catch (e: Exception) { emptyList() }
    }

    fun saveCachedAlbums(context: Context, data: List<Album>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("albums", gson.toJson(data)).apply()
    }

    fun loadCachedArtists(context: Context): List<Artist> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("artists", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Artist>>() {}.type) } catch (e: Exception) { emptyList() }
    }

    fun saveCachedArtists(context: Context, data: List<Artist>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("artists", gson.toJson(data)).apply()
    }

    fun loadCachedPlaylists(context: Context): List<Playlist> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("playlists", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Playlist>>() {}.type) } catch (e: Exception) { emptyList() }
    }

    fun saveCachedPlaylists(context: Context, data: List<Playlist>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("playlists", gson.toJson(data)).apply()
    }

    // ==========================================
    // КЭШ ПЛЕЕРА: ПОЛНЫЕ СПИСКИ (Файлы)
    // ==========================================

    suspend fun saveFullPlaylist(context: Context, sortMode: String, tracks: List<Track>) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "library_full_$sortMode.json")
                file.writeText(gson.toJson(tracks))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun loadFullPlaylist(context: Context, sortMode: String): List<Track> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "library_full_$sortMode.json")
                if (!file.exists()) return@withContext emptyList()
                gson.fromJson(file.readText(), object : TypeToken<List<Track>>() {}.type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}