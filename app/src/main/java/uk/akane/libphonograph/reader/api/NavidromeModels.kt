package uk.akane.libphonograph.reader.api

import com.squareup.moshi.Json

// Обертка ответа Subsonic
data class SubsonicResponseWrapper(
    @Json(name = "subsonic-response") val response: SubsonicResponse
)

data class SubsonicResponse(
    val status: String,
    val version: String,
    val randomSongs: RandomSongsContainer? = null
)

data class RandomSongsContainer(
    @Json(name = "song") val song: List<NavidromeSong>
)

// Наша песня с сервера
data class NavidromeSong(
    val id: String,
    val title: String,
    val album: String?,
    val artist: String?,
    val duration: Int?,
    val coverArt: String?,
    val path: String?
)