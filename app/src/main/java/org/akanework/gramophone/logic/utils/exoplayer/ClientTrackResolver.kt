package org.akanework.gramophone.logic.utils.exoplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ClientTrackResolver {
    private const val TAG = "ClientTrackResolver"
    private const val BACKEND_BASE_URL = "http://185.196.41.31"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class TrackResolveInfo(
        val trackId: Long,
        val sourceId: String,
        val isrc: String,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Int
    )

    data class CandidateV2(
        val id: String,
        val title: String,
        val uploader: String,
        val duration: Double,
        val isOfficialSong: Boolean,
        val source: String
    )

    fun resolveStreamUrl(context: Context, rawUri: Uri): Uri {
        val path = rawUri.path ?: ""
        if (!path.contains("/stream/")) {
            return rawUri
        }
        val trackIdStr = rawUri.lastPathSegment ?: return rawUri

        try {
            // 1. Fetch Track Metadata from Backend
            val info = fetchTrackInfo(trackIdStr) ?: return rawUri
            Log.d(TAG, "Fetched metadata for track ${info.trackId}: ${info.artist} - ${info.title} (ISRC: ${info.isrc})")

            // 2. Send START Telemetry
            sendTelemetry(
                type = "START",
                trackId = info.trackId,
                sourceId = info.sourceId,
                isrc = info.isrc,
                artist = info.artist,
                title = info.title,
                query = "${info.artist} ${info.title}"
            )

            // 3. Search YTM Songs from Client IP
            val queries = mutableListOf<String>()
            if (info.isrc.isNotEmpty()) {
                queries.add(info.isrc)
            }
            queries.add("${info.artist} ${info.title} Official Audio")
            queries.add("${info.artist} ${info.title}")
            queries.add(info.title)

            var bestCandidate: CandidateV2? = null
            var highestScore = -9999

            for (q in queries) {
                val candidates = searchYTM(q, info)
                for (c in candidates) {
                    val score = scoreCandidate(c, info)
                    sendTelemetry(
                        type = "SCORE_EVAL",
                        trackId = info.trackId,
                        candidateId = c.id,
                        title = c.title,
                        uploader = c.uploader,
                        candidateDur = c.duration,
                        score = score,
                        official = c.isOfficialSong
                    )
                    if (score > highestScore && score >= 100) {
                        highestScore = score
                        bestCandidate = c
                    }
                }
                if (bestCandidate != null && highestScore >= 700) {
                    break
                }
            }

            if (bestCandidate == null) {
                sendTelemetry(
                    type = "ERROR",
                    trackId = info.trackId,
                    message = "No candidates reached confidence threshold"
                )
                return rawUri
            }

            sendTelemetry(
                type = "WINNER_SELECTED",
                trackId = info.trackId,
                candidateId = bestCandidate.id,
                title = bestCandidate.title,
                score = highestScore
            )

            // 4. Extract direct audio stream URL via InnerTube Player API
            val startTime = System.currentTimeMillis()
            val directStreamUrl = extractPlayerStreamUrl(bestCandidate.id)
            val elapsed = System.currentTimeMillis() - startTime

            if (directStreamUrl.isNullOrEmpty()) {
                sendTelemetry(
                    type = "ERROR",
                    trackId = info.trackId,
                    message = "Failed to extract stream for winner VideoID ${bestCandidate.id}"
                )
                return rawUri
            }

            // 5. Send SUCCESS Telemetry
            sendTelemetry(
                type = "SUCCESS",
                trackId = info.trackId,
                candidateId = bestCandidate.id,
                streamUrl = directStreamUrl,
                duration = info.duration,
                source = "Client YTM WEB_REMIX",
                elapsedTimeMs = elapsed
            )

            Log.i(TAG, "Successfully resolved direct stream for track ${info.trackId} -> $directStreamUrl")
            return Uri.parse(directStreamUrl)

        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream URL for $rawUri", e)
            return rawUri
        }
    }

    private fun fetchTrackInfo(trackIdStr: String): TrackResolveInfo? {
        val url = "$BACKEND_BASE_URL/api/v1/tracks/${URLEncoder.encode(trackIdStr, "UTF-8")}/resolve_info"
        val req = Request.Builder().url(url).get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            return TrackResolveInfo(
                trackId = json.optLong("track_id"),
                sourceId = json.optString("source_id"),
                isrc = json.optString("isrc"),
                title = json.optString("title"),
                artist = json.optString("artist"),
                album = json.optString("album"),
                duration = json.optInt("duration")
            )
        }
    }

    private fun sendTelemetry(
        type: String,
        trackId: Long,
        sourceId: String = "",
        isrc: String = "",
        artist: String = "",
        title: String = "",
        query: String = "",
        candidateId: String = "",
        candidateDur: Double = 0.0,
        uploader: String = "",
        score: Int = 0,
        official: Boolean = false,
        streamUrl: String = "",
        duration: Int = 0,
        source: String = "",
        elapsedTimeMs: Long = 0,
        message: String = ""
    ) {
        try {
            val json = JSONObject().apply {
                put("type", type)
                put("track_id", trackId)
                if (sourceId.isNotEmpty()) put("source_id", sourceId)
                if (isrc.isNotEmpty()) put("isrc", isrc)
                if (artist.isNotEmpty()) put("artist", artist)
                if (title.isNotEmpty()) put("title", title)
                if (query.isNotEmpty()) put("query", query)
                if (candidateId.isNotEmpty()) put("candidate_id", candidateId)
                if (candidateDur > 0) put("candidate_dur", candidateDur)
                if (uploader.isNotEmpty()) put("uploader", uploader)
                if (score != 0) put("score", score)
                if (official) put("official", true)
                if (streamUrl.isNotEmpty()) put("stream_url", streamUrl)
                if (duration > 0) put("duration", duration)
                if (source.isNotEmpty()) put("source", source)
                if (elapsedTimeMs > 0) put("elapsed_time_ms", elapsedTimeMs)
                if (message.isNotEmpty()) put("message", message)
            }

            val reqBody = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$BACKEND_BASE_URL/api/v1/extractor/telemetry")
                .post(reqBody)
                .build()

            httpClient.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "Telemetry send failed: ${e.message}")
        }
    }

    private fun searchYTM(query: String, info: TrackResolveInfo): List<CandidateV2> {
        val url = "https://music.youtube.com/youtubei/v1/search"
        val reqBodyJson = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240101.01.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("query", query)
            put("params", "EgWKAQIYAWoKEAMQBBAFEA0QERgB")
        }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .post(reqBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val candidates = mutableListOf<CandidateV2>()
        val seenIds = mutableSetOf<String>()
        val targetArtistLower = info.artist.lowercase().trim()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            if (body.contains("No results for") || body.contains("Try different keywords")) {
                Log.d(TAG, "Search for '$query' returned no direct results, skipping fallback recommendations.")
                return emptyList()
            }
            val json = JSONObject(body)

            fun parse(obj: Any) {
                when (obj) {
                    is JSONObject -> {
                        // 1. Parse Top Result (musicCardShelfRenderer)
                        if (obj.has("musicCardShelfRenderer")) {
                            val card = obj.getJSONObject("musicCardShelfRenderer")
                            var vid = ""
                            var title = ""
                            var uploader = ""

                            val titleRuns = card.optJSONObject("title")?.optJSONArray("runs")
                            if (titleRuns != null && titleRuns.length() > 0) {
                                val run0 = titleRuns.optJSONObject(0)
                                title = run0?.optString("text", "") ?: ""
                                vid = run0?.optJSONObject("navigationEndpoint")
                                    ?.optJSONObject("watchEndpoint")
                                    ?.optString("videoId", "") ?: ""
                            }

                            val subRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
                            if (subRuns != null) {
                                for (i in 0 until subRuns.length()) {
                                    val r = subRuns.optJSONObject(i)
                                    val t = r?.optString("text", "") ?: ""
                                    if (t.isNotEmpty() && t != " • " && t != "Song" && t != "Video") {
                                        if (uploader.isEmpty()) uploader = t
                                    }
                                }
                            }

                            val isOfficial = true

                            if (vid.isNotEmpty() && title.isNotEmpty() && !seenIds.contains(vid)) {
                                seenIds.add(vid)
                                candidates.add(
                                    CandidateV2(
                                        id = vid,
                                        title = title,
                                        uploader = if (uploader.isNotEmpty()) uploader else info.artist,
                                        duration = 0.0,
                                        isOfficialSong = isOfficial,
                                        source = "YouTube Music Card (Top Result)"
                                    )
                                )
                            }
                        }

                        // 2. Parse List Result (musicResponsiveListItemRenderer)
                        if (obj.has("musicResponsiveListItemRenderer")) {
                            val item = obj.getJSONObject("musicResponsiveListItemRenderer")
                            var vid = ""
                            var title = ""
                            var uploader = ""
                            var isOfficial = false

                            if (item.has("playlistItemData")) {
                                vid = item.getJSONObject("playlistItemData").optString("videoId", "")
                            }

                            val flex = item.optJSONArray("flexColumns")
                            if (flex != null && flex.length() > 0) {
                                val flex0 = flex.optJSONObject(0)
                                val textObj = flex0?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                                val runs = textObj?.optJSONArray("runs")
                                if (runs != null && runs.length() > 0) {
                                    val run0 = runs.optJSONObject(0)
                                    title = run0?.optString("text", "") ?: ""
                                    if (vid.isEmpty() && run0?.has("navigationEndpoint") == true) {
                                        vid = run0.getJSONObject("navigationEndpoint")
                                            .optJSONObject("watchEndpoint")
                                            .optString("videoId", "")
                                    }
                                }
                            }

                            if (flex != null && flex.length() > 1) {
                                val flex1 = flex.optJSONObject(1)
                                val textObj = flex1?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                                val runs = textObj?.optJSONArray("runs")
                                if (runs != null) {
                                    for (i in 0 until runs.length()) {
                                        val r = runs.optJSONObject(i)
                                        val t = r?.optString("text", "") ?: ""
                                        if (t.isNotEmpty() && t != " • " && t != "Song" && t != "Video") {
                                            if (uploader.isEmpty()) {
                                                uploader = t
                                            }
                                        }
                                    }
                                }
                            }

                            val itemStr = item.toString()
                            val uploaderLower = uploader.lowercase().trim()
                            if (itemStr.contains("- Topic") || itemStr.contains("MUSIC_VIDEO_TYPE") || uploaderLower.contains(targetArtistLower) || targetArtistLower.contains(uploaderLower)) {
                                isOfficial = true
                            }

                            if (vid.isNotEmpty() && title.isNotEmpty() && !seenIds.contains(vid)) {
                                seenIds.add(vid)
                                candidates.add(
                                    CandidateV2(
                                        id = vid,
                                        title = title,
                                        uploader = uploader,
                                        duration = 0.0,
                                        isOfficialSong = isOfficial,
                                        source = "YouTube Music (WEB_REMIX)"
                                    )
                                )
                            }
                        }

                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            parse(obj.get(k))
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until obj.length()) {
                            parse(obj.get(i))
                        }
                    }
                }
            }

            parse(json)
        }
        return candidates
    }

    private fun scoreCandidate(c: CandidateV2, info: TrackResolveInfo): Int {
        val targetTitle = info.title.lowercase().trim()
        val candTitle = c.title.lowercase().trim()
        val targetArtist = info.artist.lowercase().trim()
        val candUploader = c.uploader.lowercase().trim()

        var score = 100

        val titleMatches = candTitle.contains(targetTitle) || targetTitle.contains(candTitle)
        val artistMatches = candUploader.contains(targetArtist) || targetArtist.contains(candUploader) || candTitle.contains(targetArtist)

        // Severe penalty for unwanted isolated tracks, covers, reactions, tutorials
        if (candTitle.contains("bass only") || candTitle.contains("drums only") || candTitle.contains("vocal only") ||
            candTitle.contains("isolated") || candTitle.contains("backing track") || candTitle.contains("karaoke") ||
            candTitle.contains("reaction") || candTitle.contains("review") ||
            candTitle.contains("tutorial") || candTitle.contains("lesson") || candTitle.contains("tab")) {
            return -2000 // Hard rejection so it can NEVER win
        }

        val severeVersionKeywords = listOf("acoustic", "live", "cover", "instrumental", "karaoke", "orchestral", "unplugged")
        for (vk in severeVersionKeywords) {
            val inTarget = targetTitle.contains(vk)
            val inCand = candTitle.contains(vk)
            if (inTarget && inCand) score += 500
            else if (inTarget && !inCand) score -= 500
            else if (!inTarget && inCand) score -= 800
        }

        val moderateVersionKeywords = listOf("remix", "radio edit", "deluxe", "remaster", "remastered", "extended", "edit", "slowed", "reverb", "nightcore", "demo")
        for (vk in moderateVersionKeywords) {
            val inTarget = targetTitle.contains(vk)
            val inCand = candTitle.contains(vk)
            if (inTarget && inCand) score += 400
            else if (inTarget && !inCand) score -= 400
            else if (!inTarget && inCand) score -= 500
        }

        if (!artistMatches && targetArtist.isNotEmpty() && !c.isOfficialSong) {
            return -1000 // Hard rejection if artist does not match
        }

        if (!titleMatches && targetTitle.isNotEmpty()) {
            return -1000 // Hard rejection if title does not match
        }

        if (c.isOfficialSong && (titleMatches || artistMatches)) {
            score += 1000 // Official song matching bonus
        }

        if (candTitle == targetTitle) score += 500
        else if (titleMatches) score += 250

        if (artistMatches) score += 300

        return score
    }

    private fun extractPlayerStreamUrl(videoId: String): String? {
        val url = "https://www.youtube.com/youtubei/v1/player"
        val reqBodyJson = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.02.39")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("videoId", videoId)
        }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "com.google.android.youtube/19.02.39 (Linux; U; Android 14; US)")
            .post(reqBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            var bestUrl: String? = null
            var highestBitrate = 0

            for (i in 0 until adaptiveFormats.length()) {
                val fmt = adaptiveFormats.getJSONObject(i)
                val mimeType = fmt.optString("mimeType", "")
                if (mimeType.contains("audio/")) {
                    val streamUrl = fmt.optString("url", "")
                    val bitrate = fmt.optInt("bitrate", 0)
                    if (streamUrl.isNotEmpty() && bitrate > highestBitrate) {
                        highestBitrate = bitrate
                        bestUrl = streamUrl
                    }
                }
            }
            return bestUrl
        }
    }
}
