package org.akanework.gramophone.desktop.api

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Track(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("artist")
    val artist: String,
    @SerializedName("album")
    val album: String? = null,
    @SerializedName("cover")
    val cover: String? = null,
    @SerializedName("duration")
    val duration: Int = 0,
    @SerializedName("is_liked")
    var is_liked: Boolean = false,
    @SerializedName("file_path")
    val file_path: String? = null,
    @SerializedName("is_lossless")
    val is_lossless: Boolean = false
) : Serializable

data class Album(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("artist_name")
    val artistName: String? = null,
    @SerializedName("cover")
    val cover: String? = null,
    @SerializedName("release_year")
    val releaseYear: Int? = null,
    @SerializedName("tracks")
    val tracks: List<Track>? = null,
    @SerializedName("record_type")
    val recordType: String? = "album",
    @SerializedName("has_flac")
    val hasFlac: Boolean = false,
    @SerializedName("is_liked")
    var isLiked: Boolean = false
) : Serializable

data class Artist(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("cover")
    val cover: String? = null,
    @SerializedName("picture")
    val picture: String? = null,
    @SerializedName("bio")
    val bio: String? = null,
    @SerializedName("top_tracks")
    val topTracks: List<Track>? = null,
    @SerializedName("albums")
    val albums: List<Album>? = null,
    @SerializedName("tracks")
    val tracks: List<Track>? = null,
    @SerializedName("is_liked")
    var isLiked: Boolean = false
) : Serializable

data class Playlist(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("cover_url")
    val coverUrl: String? = null,
    @SerializedName("is_public")
    val isPublic: Boolean = false,
    @SerializedName("auto_covers")
    val autoCovers: List<String> = emptyList()
) : Serializable

data class PlaylistCreateRequest(
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("is_public")
    val isPublic: Boolean = false
)

data class SearchResponse(
    @SerializedName("tracks")
    val tracks: List<Track> = emptyList(),
    @SerializedName("albums")
    val albums: List<Album> = emptyList(),
    @SerializedName("artists")
    val artists: List<Artist> = emptyList()
)
