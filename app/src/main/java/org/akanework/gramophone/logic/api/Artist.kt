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

    @SerializedName("info")
    val info: ArtistInfo? = null,

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

data class ArtistInfo(
    @SerializedName("origin")
    val origin: String? = null,

    @SerializedName("country")
    val country: String? = null,

    @SerializedName("formed_year")
    val formedYear: Int? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("biography")
    val biography: String? = null,

    @SerializedName("genres")
    val genres: List<String>? = null,

    @SerializedName("members")
    val members: List<BandMember>? = null,

    @SerializedName("milestones")
    val milestones: List<CareerMilestone>? = null,

    @SerializedName("facts")
    val facts: List<TriviaFact>? = null,

    @SerializedName("source")
    val source: String? = null
) : Serializable

data class BandMember(
    @SerializedName("name")
    val name: String,

    @SerializedName("role")
    val role: String,

    @SerializedName("status")
    val status: String, // "active" or "former"

    @SerializedName("years")
    val years: String? = null,

    @SerializedName("instruments")
    val instruments: List<String>? = null
) : Serializable

data class CareerMilestone(
    @SerializedName("year")
    val year: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String
) : Serializable

data class TriviaFact(
    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String
) : Serializable