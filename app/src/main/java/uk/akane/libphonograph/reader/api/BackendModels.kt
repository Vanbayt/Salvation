package uk.akane.libphonograph.reader.api

import com.google.gson.annotations.SerializedName

// Ответ от сервера поиска
data class BackendTrackDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("duration") val duration: Int,
    @SerializedName("source") val source: String
)

// Простая моделька для UI списка (чтобы не тащить всю DTO)
data class StreamItem(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val streamUrl: String
)