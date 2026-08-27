package org.akanework.gramophone.logic.api

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
data class PresetCard(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("subtitle") val subtitle: String,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("gradient_hex") val gradientHex: List<String> = emptyList(),
    @SerializedName("mood_tag") val moodTag: String = "",
    @SerializedName("is_contrast") val isContrast: Boolean = false,
    @SerializedName("seed_query") val seedQuery: String = ""
) : Serializable

@Keep
data class RadioResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("seed_track") val seedTrack: Track?,
    @SerializedName("tracks") val tracks: List<RadioCandidate>
) : Serializable

@Keep
data class RadioCandidate(
    @SerializedName("track_id") val trackId: Long? = null,
    @SerializedName("source_id") val sourceId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("score") val score: Double = 0.0,
    @SerializedName("sources") val sources: List<String> = emptyList()
) : Serializable

@Keep
data class RecommendationFeedbackRequest(
    @SerializedName("track_id") val trackId: Long,
    @SerializedName("event_type") val eventType: String, // play, like, skip, quick_skip, complete
    @SerializedName("device_type") val deviceType: String? = null,
    @SerializedName("listen_duration_sec") val listenDurationSec: Int = 0
) : Serializable
