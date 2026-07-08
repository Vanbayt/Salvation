package uk.akane.libphonograph.reader

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.Log
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.items.RawPlaylist
import uk.akane.libphonograph.items.EXTRA_ALBUM_ID
import uk.akane.libphonograph.items.EXTRA_ARTIST_ID
import uk.akane.libphonograph.items.EXTRA_AUTHOR
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import uk.akane.libphonograph.utils.MiscUtils
import java.io.File
import java.io.IOException

// Импорты для Navidrome
import kotlinx.coroutines.runBlocking
import uk.akane.libphonograph.reader.api.NetworkModule

internal object Reader {

    suspend fun readFromMediaStore(
        context: Context,
        minSongLengthSeconds: Long = 0,
        blackListSet: Set<String> = setOf(),
        shouldUseEnhancedCoverReading: Boolean? = false,
        shouldIncludeExtraFormat: Boolean = true,
        shouldLoadAlbums: Boolean = true,
        shouldLoadArtists: Boolean = true,
        shouldLoadGenres: Boolean = true,
        shouldLoadDates: Boolean = true,
        shouldLoadFolders: Boolean = true,
        shouldLoadFilesystem: Boolean = true,
        shouldLoadIdMap: Boolean = true,
        coverStubUri: String? = null
    ): ReaderResult {

        // 1. Инициализация (заглушки)
        val songs = mutableListOf<MediaItem>()
        val idMap = if (shouldLoadIdMap) hashMapOf<Long, MediaItem>() else null
        val pathMap = if (shouldLoadIdMap) hashMapOf<String, MediaItem>() else null

        // ВАЖНО: Карта альбомов (для обложек)
        val albumMap = if (shouldLoadAlbums) hashMapOf<Long, MiscUtils.AlbumImpl>() else null

        val albumList = mutableListOf<Album>()
        val albumArtistList = mutableListOf<Artist>()
        val artistList = mutableListOf<Artist>()
        val genreList = mutableListOf<Genre>()
        val dateList = mutableListOf<Date>()

        val root = if (shouldLoadFilesystem) MiscUtils.FileNodeImpl("storage") else null
        val shallowRoot = if (shouldLoadFolders) MiscUtils.FileNodeImpl("shallow") else null
        val folders = if (shouldLoadFolders) hashSetOf<String>() else null

        // --- NAVIDROME БЛОК ---
        val myUser = "vanbayt"
        val myPass = "0150asdf" // <--- ВПИШИ ПАРОЛЬ!
        val myBaseUrl = "http://100.90.101.37:4533/"

        try {
            val response = runBlocking {
                // Берем 500 песен
                NetworkModule.api.getRandomSongs(
                    user = myUser,
                    pass = myPass,
                    size = 500
                )
            }

            val navidromeSongs = response.response.randomSongs?.song ?: emptyList()

            for (nSong in navidromeSongs) {
                // Генерируем уникальные ID
                val nativeId = nSong.id.hashCode().toLong()
                val albumId = (nSong.album ?: "Unknown").hashCode().toLong()
                val artistId = (nSong.artist ?: "Unknown").hashCode().toLong()

                // Ссылка на поток
                val streamUrl = android.net.Uri.parse(
                    "${myBaseUrl}rest/stream?u=$myUser&p=$myPass&v=1.16.1&c=Gramophone&id=${nSong.id}"
                )

                // Ссылка на обложку
                val artUrl = if (nSong.coverArt != null) {
                    android.net.Uri.parse(
                        "${myBaseUrl}rest/getCoverArt?u=$myUser&p=$myPass&v=1.16.1&c=Gramophone&id=${nSong.coverArt}&size=600&f=jpg"
                    )
                } else null

                val extras = Bundle().apply {
                    putLong(EXTRA_ALBUM_ID, albumId)
                    putLong(EXTRA_ARTIST_ID, artistId)
                    putString(EXTRA_AUTHOR, nSong.artist)
                }

                val song = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaId(nSong.id)
                    .setMimeType("audio/mpeg")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setTitle(nSong.title)
                            .setArtist(nSong.artist)
                            .setAlbumTitle(nSong.album)
                            .setArtworkUri(artUrl)
                            .setDurationMs((nSong.duration ?: 0) * 1000L)
                            .setExtras(extras)
                            .build()
                    )
                    .build()

                songs.add(song)
                idMap?.put(nativeId, song)

                // Заполняем альбомы
                if (shouldLoadAlbums && albumMap != null) {
                    val albumObj = albumMap.getOrPut(albumId) {
                        MiscUtils.AlbumImpl(
                            albumId,
                            nSong.album ?: "Unknown",
                            null, null,
                            artUrl,
                            null, null, null,
                            mutableListOf()
                        )
                    }
                    albumObj.songList.add(song)
                }
            }

            // Финализируем список альбомов
            if (albumMap != null) {
                albumList.addAll(albumMap.values)
            }

        } catch (e: Exception) {
            android.util.Log.e("NAVIDROME", "Error: ${e.message}", e)
        }
        // --- КОНЕЦ БЛОКА ---

        return ReaderResult(
            songs,
            albumList,
            albumArtistList,
            artistList,
            genreList,
            dateList,
            idMap,
            pathMap,
            root,
            shallowRoot,
            folders
        )
    }

    fun fetchPlaylists(context: Context): Pair<List<RawPlaylist>, Boolean> {
        return Pair(emptyList(), false)
    }
}