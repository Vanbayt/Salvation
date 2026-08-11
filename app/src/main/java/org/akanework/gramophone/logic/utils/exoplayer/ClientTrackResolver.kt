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

    @Volatile
    private var lastVisitorData: String = ""

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

    private val resolvedCache = android.util.LruCache<Long, Pair<String, Long>>(50)

    fun resolveStreamUrl(context: Context, rawUri: Uri): Uri {
        val path = rawUri.path ?: ""
        if (!path.contains("/stream/")) {
            return rawUri
        }
        val trackIdStr = rawUri.lastPathSegment ?: return rawUri
        val trackIdLong = trackIdStr.toLongOrNull()

        if (trackIdLong != null) {
            val cached = resolvedCache.get(trackIdLong)
            if (cached != null && (System.currentTimeMillis() - cached.second) < 3 * 3600 * 1000L) {
                Log.i(TAG, "⚡ [MEM_CACHE_HIT] Track $trackIdLong resolved instantly from device RAM!")
                return Uri.parse(cached.first)
            }
        }

        try {
            // 1. Fetch Track Metadata from Backend
            val info = fetchTrackInfo(trackIdStr) ?: return rawUri
            Log.d(TAG, "Fetched metadata for track ${info.trackId}: ${info.artist} - ${info.title} (SourceID: ${info.sourceId})")

            org.akanework.gramophone.logic.utils.SmartPlaybackManager.onTrackRequested("${info.artist} - ${info.title}")

            // 🔥 ПРЯМОЙ ПУТЬ ДЛЯ YOUTUBE MUSIC ТРЕКОВ: Зашитый Video ID не требует повторного поиска
            val directVideoId = when {
                info.sourceId.startsWith("ytmusic_") -> info.sourceId.removePrefix("ytmusic_")
                info.sourceId.startsWith("yt_") -> info.sourceId.removePrefix("yt_")
                info.sourceId.length == 11 && !info.sourceId.contains(" ") -> info.sourceId
                else -> ""
            }

            if (directVideoId.isNotEmpty()) {
                Log.d(TAG, "⚡ Direct Video ID recognized: $directVideoId. Bypassing client-side search!")
                val directUrl = extractPlayerStreamUrl(directVideoId, info.duration)
                if (!directUrl.isNullOrEmpty()) {
                    sendTelemetry(
                        type = "WINNER_SELECTED",
                        trackId = info.trackId,
                        candidateId = directVideoId,
                        title = info.title,
                        score = 2000
                    )
                    resolvedCache.put(info.trackId, Pair(directUrl, System.currentTimeMillis()))
                    return Uri.parse(directUrl)
                } else {
                    // 🔥 ЕСЛИ КЛИЕНТСКИЙ ИЗВЛЕКАТЕЛЬ НЕ СМОГ ПОЛУЧИТЬ ПРЯМУЮ ССЫЛКУ:
                    // ВОЗВРАЩАЕМ rawUri (http://185.196.41.31/stream/...), ЧТОБЫ СЕРВЕР САМ ОТДАЛ ПОТОК!
                    // КАТЕГОРИЧЕСКИ ЗАПРЕЩАЕМ ЗАПУСК КЛИЕНТСКОГО ПОИСКА!
                    Log.w(TAG, "Client direct extraction failed for $directVideoId. Delegating streaming directly to server stream URL!")
                    return rawUri
                }
            }

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

            val scoredCandidates = mutableListOf<Pair<CandidateV2, Int>>()
            val seenCandidateIds = mutableSetOf<String>()

            for (q in queries) {
                val candidates = searchYTM(q, info)
                for (c in candidates) {
                    if (seenCandidateIds.contains(c.id)) continue
                    seenCandidateIds.add(c.id)

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
                    if (score >= 100) {
                        scoredCandidates.add(Pair(c, score))
                    }
                }
                if (scoredCandidates.any { it.second >= 700 }) {
                    break
                }
            }

            scoredCandidates.sortByDescending { it.second }

            if (scoredCandidates.isEmpty()) {
                sendTelemetry(
                    type = "ERROR",
                    trackId = info.trackId,
                    message = "No candidates reached confidence threshold"
                )
                return rawUri
            }

            var winnerCandidate: CandidateV2? = null
            var winnerUrl: String? = null
            var winnerScore = 0
            val startTime = System.currentTimeMillis()

            for ((cand, score) in scoredCandidates.take(5)) {
                val directUrl = extractPlayerStreamUrl(cand.id, info.duration)
                if (!directUrl.isNullOrEmpty()) {
                    winnerCandidate = cand
                    winnerUrl = directUrl
                    winnerScore = score
                    break
                } else {
                    sendTelemetry(
                        type = "STREAM_WARNING",
                        trackId = info.trackId,
                        candidateId = cand.id,
                        message = "Failed to extract stream for Candidate ${cand.id}, trying next candidate..."
                    )
                }
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (winnerCandidate == null || winnerUrl.isNullOrEmpty()) {
                sendTelemetry(
                    type = "ERROR",
                    trackId = info.trackId,
                    message = "All top candidates failed stream extraction"
                )
                org.akanework.gramophone.logic.utils.SmartPlaybackManager.onHardFailure("Варианты трека не найдены", "${info.artist} - ${info.title}")
                return rawUri
            }

            sendTelemetry(
                type = "WINNER_SELECTED",
                trackId = info.trackId,
                candidateId = winnerCandidate.id,
                title = winnerCandidate.title,
                score = winnerScore
            )

            // 5. Send SUCCESS Telemetry
            sendTelemetry(
                type = "SUCCESS",
                trackId = info.trackId,
                candidateId = winnerCandidate.id,
                streamUrl = winnerUrl,
                duration = info.duration,
                source = "Client YTM WEB_REMIX",
                elapsedTimeMs = elapsed
            )

            resolvedCache.put(info.trackId, Pair(winnerUrl, System.currentTimeMillis()))
            Log.i(TAG, "Successfully resolved direct stream for track ${info.trackId} -> $winnerUrl")
            return Uri.parse(winnerUrl)

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

            val logMsg = when (type) {
                "START" -> "Resolving track $trackId ($artist - $title) | ISRC: $isrc"
                "WINNER_SELECTED" -> "Winner selected for track $trackId: VideoID=$candidateId, Title='$title', Score=$score"
                "SUCCESS" -> "Success for track $trackId -> VideoID=$candidateId ($source) in ${elapsedTimeMs}ms"
                "ERROR" -> "Error for track $trackId: $message"
                else -> "Telemetry $type: VideoID=$candidateId, Score=$score $message"
            }
            org.akanework.gramophone.logic.utils.PlaybackLogger.log(type, logMsg)
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
            val vData = json.optJSONObject("responseContext")?.optString("visitorData", "") ?: ""
            if (vData.isNotEmpty()) {
                lastVisitorData = vData
            }

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

                            var cardDur = 0.0
                            val subRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
                            if (subRuns != null) {
                                for (i in 0 until subRuns.length()) {
                                    val r = subRuns.optJSONObject(i)
                                    val t = r?.optString("text", "") ?: ""
                                    if (t.contains(":")) {
                                        val d = parseDurationSec(t)
                                        if (d > 0) cardDur = d
                                    } else if (t.isNotEmpty() && t != " • " && t != "Song" && t != "Video") {
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
                                        duration = cardDur,
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
                            var candDur = 0.0
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

                            if (vid.isEmpty() && item.has("overlay")) {
                                vid = item.optJSONObject("overlay")
                                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                                    ?.optJSONObject("content")
                                    ?.optJSONObject("musicPlayButtonRenderer")
                                    ?.optJSONObject("playNavigationEndpoint")
                                    ?.optJSONObject("watchEndpoint")
                                    ?.optString("videoId", "") ?: ""
                            }

                            if (flex != null) {
                                for (fIdx in 1 until flex.length()) {
                                    val flexObj = flex.optJSONObject(fIdx)
                                    val textObj = flexObj?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                                    val runs = textObj?.optJSONArray("runs")
                                    if (runs != null) {
                                        for (i in 0 until runs.length()) {
                                            val r = runs.optJSONObject(i)
                                            val t = r?.optString("text", "") ?: ""
                                            if (t.contains(":")) {
                                                val d = parseDurationSec(t)
                                                if (d > 0) candDur = d
                                            } else if (t.isNotEmpty() && t != " • " && t != "Song" && t != "Video") {
                                                if (uploader.isEmpty()) {
                                                    uploader = t
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val itemStr = item.toString()
                            val uploaderLower = uploader.lowercase().trim()
                            if (itemStr.contains("- Topic") || itemStr.contains("MUSIC_VIDEO_TYPE_ATV") || uploaderLower == targetArtistLower || uploaderLower == "$targetArtistLower topic") {
                                isOfficial = true
                            }

                            if (vid.isNotEmpty() && title.isNotEmpty() && !seenIds.contains(vid)) {
                                seenIds.add(vid)
                                candidates.add(
                                    CandidateV2(
                                        id = vid,
                                        title = title,
                                        uploader = uploader,
                                        duration = candDur,
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
        var score = 100

        val primaryArtistClean = cleanNormalize(info.artist.split(";")[0].split(",")[0])
        val cUploaderClean = cleanNormalize(c.uploader)
        val cTitleClean = cleanNormalize(c.title)
        val tTitleClean = cleanNormalize(info.title)

        val targetCore = extractCoreTitle(info.title, primaryArtistClean)
        val candCore = extractCoreTitle(c.title, primaryArtistClean)

        val titleMatches = (cTitleClean == tTitleClean) ||
                (targetCore.isNotEmpty() && candCore.isNotEmpty() && targetCore == candCore)

        val artistMatches = primaryArtistClean.isNotEmpty() && (
            cUploaderClean == primaryArtistClean ||
            cUploaderClean == "$primaryArtistClean topic" ||
            cUploaderClean == "$primaryArtistClean vevo" ||
            cUploaderClean == "$primaryArtistClean official" ||
            cUploaderClean == "$primaryArtistClean official channel" ||
            (cUploaderClean.startsWith("$primaryArtistClean ") && (cUploaderClean.contains("topic") || cUploaderClean.contains("vevo") || cUploaderClean.contains("official"))) ||
            (cUploaderClean.isEmpty() && (cTitleClean.startsWith("$primaryArtistClean ") || cTitleClean.endsWith(" $primaryArtistClean") || cTitleClean == primaryArtistClean))
        )

        // Severe penalty for unwanted isolated tracks, covers, reactions, tutorials
        if (candTitle.contains("bass only") || candTitle.contains("drums only") || candTitle.contains("vocal only") ||
            candTitle.contains("isolated") || candTitle.contains("backing track") || candTitle.contains("karaoke") ||
            candTitle.contains("reaction") || candTitle.contains("review") ||
            candTitle.contains("tutorial") || candTitle.contains("lesson") || candTitle.contains("tab")) {
            return -2000 // Hard rejection so it can NEVER win
        }

        val severeVersionKeywords = listOf(
            "acoustic", "live", "cover", "instrumental", "karaoke", "orchestral", "unplugged",
            "keyboards", "keyboard", "guitar", "guitars", "bass", "drums", "vocal", "vocals",
            "piano", "synth", "arrangement", "raw session", "raw sessions", "outtake", "rough mix",
            "alternate take", "demo"
        )
        for (vk in severeVersionKeywords) {
            val inTarget = targetTitle.contains(vk)
            val inCand = candTitle.contains(vk) || cUploaderClean.contains(vk)
            if (inTarget && inCand) score += 500
            else if (inTarget && !inCand) score -= 500
            else if (!inTarget && inCand) score -= 1500
        }

        val moderateVersionKeywords = listOf("remix", "radio edit", "deluxe", "remaster", "extended", "slowed", "reverb", "nightcore", "8-bit")
        for (vk in moderateVersionKeywords) {
            val inTarget = targetTitle.contains(vk)
            val inCand = candTitle.contains(vk) || cUploaderClean.contains(vk)
            if (inTarget && inCand) score += 400
            else if (inTarget && !inCand) score -= 400
            else if (!inTarget && inCand) score -= 500
        }

        if (!c.title.contains("(") && !c.title.contains("[")) {
            score += 50 // Bonus for clean studio title without extra parentheses
        }

        if (!artistMatches && primaryArtistClean.isNotEmpty()) {
            score -= 1000 // Penalty if candidate artist does not match requested artist
        }

        if (!titleMatches && targetTitle.isNotEmpty()) {
            return -1000 // Hard rejection if title does not match
        }

        if (c.isOfficialSong && titleMatches && artistMatches) {
            score += 1000 // Official song matching bonus (only when artist and title match)
        }

        if (candTitle == targetTitle) score += 500
        else if (titleMatches) score += 250

        if (artistMatches) score += 300

        val targetDur = info.duration.toDouble()
        if (targetDur > 0 && c.duration > 0) {
            val diff = Math.abs(targetDur - c.duration)
            if (diff > 15 || (c.duration < 0.6 * targetDur && diff > 10)) {
                return -2000 // Hard rejection for duration mismatch > 15s
            } else if (diff > 5) {
                score -= (diff * 30).toInt()
            } else if (diff <= 3) {
                score += 200
            }
        }

        return score
    }

    private fun parseDurationSec(s: String): Double {
        val clean = s.trim()
        if (!clean.contains(":")) return 0.0
        val parts = clean.split(":")
        try {
            if (parts.size == 2) {
                return (parts[0].toDouble() * 60.0) + parts[1].toDouble()
            } else if (parts.size == 3) {
                return (parts[0].toDouble() * 3600.0) + (parts[1].toDouble() * 60.0) + parts[2].toDouble()
            }
        } catch (e: Exception) {
            return 0.0
        }
        return 0.0
    }

    private fun cleanNormalize(s: String): String {
        return s.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^a-z0-9а-я]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractCoreTitle(title: String, artist: String = ""): String {
        var t = title
        if (t.contains(" - ")) {
            val parts = t.split(" - ")
            val artClean = cleanNormalize(artist)
            val p0Clean = cleanNormalize(parts[0])
            if (artClean.isNotEmpty() && p0Clean == artClean) {
                t = parts.drop(1).joinToString(" - ")
            }
        }
        val subParts = t.split(Regex("\\(|\\[|-|\\bfeat\\b|\\blive\\b|\\bremix\\b", RegexOption.IGNORE_CASE))
        return cleanNormalize(if (subParts.isNotEmpty()) subParts[0] else t)
    }

    private fun extractPlayerStreamUrl(videoId: String, targetDurationSec: Int = 0): String? {
        data class ProfileSpec(val name: String, val version: String, val userAgent: String, val screen: String? = null)

        val profiles = listOf(
            ProfileSpec("ANDROID", "19.05.36", "com.google.android.youtube/19.05.36 (Linux; U; Android 14; en_US)", "WATCH"),
            ProfileSpec("IOS", "19.29.1", "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)"),
            ProfileSpec("WEB_REMIX", "1.20240101.01.00", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
        )

        for (prof in profiles) {
            try {
                val url = "https://www.youtube.com/youtubei/v1/player"
                val reqBodyJson = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", prof.name)
                            put("clientVersion", prof.version)
                            put("hl", "en")
                            put("gl", "US")
                            if (prof.screen != null) {
                                put("clientScreen", prof.screen)
                            }
                            if (lastVisitorData.isNotEmpty()) {
                                put("visitorData", lastVisitorData)
                            }
                        })
                    })
                    put("videoId", videoId)
                }

                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", prof.userAgent)
                    .post(reqBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val playability = json.optJSONObject("playabilityStatus")
                    val status = playability?.optString("status", "") ?: ""
                    if (status != "OK") return@use

                    val videoDetails = json.optJSONObject("videoDetails")
                    val playerLengthSec = videoDetails?.optString("lengthSeconds", "0")?.toIntOrNull() ?: 0
                    if (targetDurationSec > 0 && playerLengthSec > 0) {
                        val diff = Math.abs(targetDurationSec - playerLengthSec)
                        if (diff > 30 || (playerLengthSec < 0.6 * targetDurationSec && diff > 15)) {
                            Log.w(TAG, "Rejected candidate $videoId due to exact Player duration mismatch: ${playerLengthSec}s vs ${targetDurationSec}s")
                            return null
                        }
                    }

                    val streamingData = json.optJSONObject("streamingData") ?: return@use
                    val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return@use

                    var bestUrl: String? = null
                    var bestScore = -1

                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.getJSONObject(i)
                        val mimeType = fmt.optString("mimeType", "")
                        val itag = fmt.optInt("itag", 0)
                        if (mimeType.contains("audio/")) {
                            var streamUrl = fmt.optString("url", "")
                            if (streamUrl.isEmpty() && fmt.has("signatureCipher")) {
                                val cipher = fmt.getString("signatureCipher")
                                val params = cipher.split("&")
                                for (p in params) {
                                    if (p.startsWith("url=")) {
                                        streamUrl = java.net.URLDecoder.decode(p.substring(4), "UTF-8")
                                        break
                                    }
                                }
                            }
                            if (streamUrl.isNotEmpty()) {
                                val bitrate = fmt.optInt("bitrate", 0)
                                val isM4A = mimeType.contains("audio/mp4") || mimeType.contains("audio/m4a") || itag == 140
                                // M4A AAC (itag 140) receives priority bonus to bypass YouTube SABR throttling on WebM Opus
                                val formatScore = bitrate + (if (isM4A) 1000000 else 0)
                                if (formatScore > bestScore) {
                                    bestScore = formatScore
                                    bestUrl = streamUrl
                                }
                            }
                        }
                    }

                    if (!bestUrl.isNullOrEmpty()) {
                        Log.i(TAG, "Successfully extracted direct audio URL using profile ${prof.name} for VideoID $videoId")
                        return bestUrl
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed profile ${prof.name} for VideoID $videoId: ${e.message}")
            }
        }
        return null
    }
}
