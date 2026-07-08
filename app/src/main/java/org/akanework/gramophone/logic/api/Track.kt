package org.akanework.gramophone.logic.api

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Track(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String,

    // Имя исполнителя (для быстрых списков)
    @SerializedName("artist")
    val artist: String,

    // Имя альбома
    @SerializedName("album")
    val album: String? = null,

    // Обложка трека (может наследоваться от альбома)
    @SerializedName("cover")
    val cover: String? = null,

    @SerializedName("duration")
    val duration: Int = 0,

    @SerializedName("is_liked")
    var is_liked: Boolean = false,

    @SerializedName("file_path")
    val file_path: String? = null,

    @SerializedName("is_lossless")
    val is_lossless: Boolean = false,

    @SerializedName("created_at")
    val created_at: String? = null,

    @SerializedName("replay_gain")
    val replayGain: Float = 0f,

    @SerializedName("artist_id")
    val artistId: String? = null,

    @SerializedName("album_id")
    val albumId: String? = null,
) : Serializable