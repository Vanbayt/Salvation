package org.akanework.gramophone.desktop.cache

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.akanework.gramophone.desktop.api.Album
import org.akanework.gramophone.desktop.api.Artist
import org.akanework.gramophone.desktop.api.Playlist
import org.akanework.gramophone.desktop.api.Track
import java.io.File

object DesktopCacheManager {
    private val gson = Gson()
    private val cacheDir: File by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        val baseDir = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "Salvation/cache")
                else File(System.getProperty("user.home", "."), ".salvation/cache")
            }
            else -> {
                val xdgConfig = System.getenv("XDG_CONFIG_HOME")
                if (!xdgConfig.isNullOrBlank()) File(xdgConfig, "salvation/cache")
                else File(System.getProperty("user.home", "."), ".config/salvation/cache")
            }
        }
        if (!baseDir.exists()) baseDir.mkdirs()
        baseDir
    }

    // Tracks
    fun loadCachedTracks(): List<Track> {
        return try {
            val file = File(cacheDir, "tracks.json")
            if (file.exists()) {
                val type = object : TypeToken<List<Track>>() {}.type
                gson.fromJson(file.readText(), type) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCachedTracks(tracks: List<Track>) {
        try {
            File(cacheDir, "tracks.json").writeText(gson.toJson(tracks))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Albums
    fun loadCachedAlbums(): List<Album> {
        return try {
            val file = File(cacheDir, "albums.json")
            if (file.exists()) {
                val type = object : TypeToken<List<Album>>() {}.type
                gson.fromJson(file.readText(), type) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCachedAlbums(albums: List<Album>) {
        try {
            File(cacheDir, "albums.json").writeText(gson.toJson(albums))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Playlists
    fun loadCachedPlaylists(): List<Playlist> {
        return try {
            val file = File(cacheDir, "playlists.json")
            if (file.exists()) {
                val type = object : TypeToken<List<Playlist>>() {}.type
                gson.fromJson(file.readText(), type) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCachedPlaylists(playlists: List<Playlist>) {
        try {
            File(cacheDir, "playlists.json").writeText(gson.toJson(playlists))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Artists
    fun loadCachedArtists(): List<Artist> {
        return try {
            val file = File(cacheDir, "artists.json")
            if (file.exists()) {
                val type = object : TypeToken<List<Artist>>() {}.type
                gson.fromJson(file.readText(), type) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCachedArtists(artists: List<Artist>) {
        try {
            File(cacheDir, "artists.json").writeText(gson.toJson(artists))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
