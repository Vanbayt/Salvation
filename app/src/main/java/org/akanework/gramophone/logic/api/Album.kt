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
    // Может быть null, если мы грузим просто список альбомов для карточек,
    // и заполнен, когда мы открываем конкретный альбом.
    @SerializedName("tracks")
    val tracks: List<Track>? = null,

    @SerializedName("record_type")
    val recordType: String? = "album",

    // 🔥 ДОБАВЛЯЕМ В КЛАСС Album
    @SerializedName("is_liked")
    var isLiked: Boolean = false
) : Serializable