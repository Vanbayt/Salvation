package org.akanework.gramophone.logic.api

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Artist(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    // Фотография артиста (Yandex Music / Deezer)
    @SerializedName("cover")
    val cover: String? = null,

    @SerializedName("picture")
    val picture: String? = null,

    @SerializedName("bio")
    val bio: String? = null,

    // Топ-5 или Топ-10 треков исполнителя
    @SerializedName("top_tracks")
    val topTracks: List<Track>? = null,

    // Вся дискография исполнителя (список альбомов, EP, синглов)
    @SerializedName("albums")
    val albums: List<Album>? = null,

    val tracks: List<Track>? = null,

    // 🔥 ДОБАВЛЯЕМ В КЛАСС Artist
    @SerializedName("is_liked")
    var isLiked: Boolean = false
) : Serializable