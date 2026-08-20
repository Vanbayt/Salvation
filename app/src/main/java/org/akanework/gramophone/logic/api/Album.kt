package org.akanework.gramophone.logic.api

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Album(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String,

    // Кто выпустил альбом
    @SerializedName("artist_name")
    val artistName: String,

    // Обложка альбома (в высоком качестве)
    @SerializedName("cover")
    val cover: String? = null,

    @SerializedName("release_year")
    val releaseYear: Int? = null,

    // Список треков, входящих в этот альбом.
    @SerializedName("tracks")
    val tracks: List<Track>? = null,

    @SerializedName("record_type")
    val recordType: String? = "album",

    @SerializedName("artist_id")
    val artistId: String? = null,

    @SerializedName("album_id")
    val albumId: String? = null,

    @SerializedName("is_liked")
    var isLiked: Boolean = false,

    @SerializedName("info")
    val info: AlbumInfo? = null
) : Serializable

data class AlbumInfo(
    @SerializedName("overview")
    val overview: String? = null,

    @SerializedName("release_date")
    val releaseDate: String? = null,

    @SerializedName("label")
    val label: String? = null,

    @SerializedName("producers")
    val producers: List<String>? = null,

    @SerializedName("studios")
    val studios: List<String>? = null,

    @SerializedName("concept_themes")
    val conceptThemes: String? = null,

    @SerializedName("cover_story")
    val coverStory: String? = null,

    @SerializedName("singles")
    val singles: List<AlbumSingle>? = null,

    @SerializedName("reception_awards")
    val receptionAwards: String? = null,

    @SerializedName("record_type")
    val recordType: String? = null,

    @SerializedName("source")
    val source: String? = null
) : Serializable

data class AlbumSingle(
    @SerializedName("title")
    val title: String,

    @SerializedName("note")
    val note: String? = null
) : Serializable