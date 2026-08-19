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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ClientTrackResolver {
    private const val TAG = "ClientTrackResolver"
    private const val BACKEND_BASE_URL = "http://185.196.41.31"

    val cookieJar = object : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                cookieStore.removeAll { c -> cookies.any { it.name == c.name } }
                cookieStore.addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            synchronized(cookieStore) {
                cookieStore.removeAll { it.expiresAt < now }
                return cookieStore.filter { it.matches(url) }
            }
        }
    }

    @Volatile
    private var lastVisitorData: String = ""

    @Volatile
    private var currentSignatureTimestamp: Int = 20681

    private val resolveInfoCache = java.util.concurrent.ConcurrentHashMap<String, TrackResolveInfo>()
    private val directStreamCache = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val uriToTrackIdMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val urlToUserAgentMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getDirectStreamUrl(trackId: Long): String? = directStreamCache[trackId]

    fun getUserAgentForUrl(url: String): String {
        val mapped = urlToUserAgentMap[url]
        if (!mapped.isNullOrEmpty()) return mapped
        if (url.contains("c=VISIONOS") || url.contains("c%3DVISIONOS")) {
            return "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
        }
        if (url.contains("c=ANDROID_VR") || url.contains("c%3DANDROID_VR")) {
            return "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1)"
        }
        if (url.contains("c=IOS") || url.contains("c%3DIOS")) {
            return "com.google.ios.youtube/19.29.1 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X; en_US)"
        }
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }

    fun findTrackIdForUri(uri: Uri): String? {
        val seg = uri.lastPathSegment ?: ""
        if (seg.toLongOrNull() != null) return seg
        return uriToTrackIdMap[uri.toString()]
    }

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    data class ExtractorProfile(
        val name: String,
        val version: String,
        val uaPlayer: String,
        val uaDownload: String,
        val deviceMake: String = "",
        val deviceModel: String = "",
        val sdkVersion: Int = 0,
        val osName: String = "",
        val osVersion: String = "",
        val clientNameHeader: String = ""
    )

    private val profileVisitorDataMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getOrFetchVisitorData(prof: ExtractorProfile? = null): String {
        val clientKey = prof?.name ?: "WEB_REMIX"
        val cached = profileVisitorDataMap[clientKey]
        if (!cached.isNullOrEmpty()) return cached

        val clientVersion = prof?.version ?: "1.20240101.01.00"
        val clientUA = prof?.uaPlayer ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

        try {
            val reqBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", clientKey)
                        put("clientVersion", clientVersion)
                        put("hl", "en")
                        put("gl", "US")
                        if (prof != null) {
                            if (prof.deviceMake.isNotEmpty()) put("deviceMake", prof.deviceMake)
                            if (prof.deviceModel.isNotEmpty()) put("deviceModel", prof.deviceModel)
                            if (prof.sdkVersion > 0) put("androidSdkVersion", prof.sdkVersion)
                            if (prof.osName.isNotEmpty()) put("osName", prof.osName)
                            if (prof.osVersion.isNotEmpty()) put("osVersion", prof.osVersion)
                        }
                    })
                })
            }
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id")
                .header("User-Agent", clientUA)
                .post(reqBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val vData = json.optJSONObject("responseContext")?.optString("visitorData", "") ?: ""
                    if (vData.isNotEmpty()) {
                        profileVisitorDataMap[clientKey] = vData
                        lastVisitorData = vData
                        Log.i(TAG, "Bootstrapped visitorData for $clientKey from visitor_id API: ${vData.take(30)}...")
                        return vData
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "visitor_id API bootstrap for $clientKey failed: ${e.message}")
        }

        try {
            // Method 2: Fallback from youtube.com homepage HTML
            val req = Request.Builder()
                .url("https://www.youtube.com/")
                .header("User-Agent", clientUA)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val m = Regex("\"visitorData\":\\s*\"([^\"]+)\"").find(body)
                if (m != null && m.groupValues.size > 1) {
                    val vData = m.groupValues[1]
                    profileVisitorDataMap[clientKey] = vData
                    lastVisitorData = vData
                    Log.i(TAG, "Bootstrapped visitorData from HTML: ${lastVisitorData.take(30)}...")
                    return vData
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTML visitorData bootstrap failed: ${e.message}")
        }
        return lastVisitorData
    }

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
        val source: String,
        val isIsrcMatch: Boolean = false
    )

    private val resolvedCache = android.util.LruCache<Long, Pair<String, Long>>(50)

    fun invalidateCache(trackId: Long) {
        val existing = resolvedCache.remove(trackId)
        if (existing != null) {
            Log.w(TAG, "Cache invalidated for track $trackId (expired/failed URL purged)")
            org.akanework.gramophone.logic.utils.PlaybackLogger.log("CACHE_INVALIDATED", "Purged cached stream URL for track $trackId")
        }
    }

    fun reportStreamFailure(trackId: Long, failedUrl: String) {
        invalidateCache(trackId)
    }

    fun resolveStreamUrl(context: Context, rawUri: Uri, forceRefresh: Boolean = false): Uri {
        val path = rawUri.path ?: ""
        if (!path.contains("/stream/")) {
            return rawUri
        }
        val trackIdStr = rawUri.lastPathSegment ?: return rawUri
        val trackIdLong = trackIdStr.toLongOrNull()

        if (forceRefresh && trackIdLong != null) {
            invalidateCache(trackIdLong)
        }

        if (!forceRefresh && trackIdLong != null) {
            val cached = resolvedCache.get(trackIdLong)
            if (cached != null && (System.currentTimeMillis() - cached.second) < 3 * 3600 * 1000L) {
                Log.i(TAG, "⚡ [MEM_CACHE_HIT] Track $trackIdLong resolved instantly from device RAM!")
                org.akanework.gramophone.logic.utils.PlaybackLogger.log("RESOLVE_CACHE", "⚡ [MEM_CACHE_HIT] Track $trackIdLong resolved from RAM cache")
                return Uri.parse(cached.first)
            }
        }

        try {
            PoTokenProvider.initAsync(context)
            // 1. Fetch Track Metadata from Backend
            val info = fetchTrackInfo(trackIdStr) ?: return rawUri
            Log.d(TAG, "Fetched metadata for track ${info.trackId}: ${info.artist} - ${info.title} (SourceID: ${info.sourceId})")

            org.akanework.gramophone.logic.utils.SmartPlaybackManager.onTrackRequested("${info.artist} - ${info.title}")

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

            // 3. Search YTM & Standard YT from Client IP
            val primaryArtist = info.artist.split(";")[0].split(",")[0].trim()
            val cleanTitle = info.title
                .replace(Regex("(?i)[-\\(\\[]\\s*remaster(ed)?\\s*(\\d{4})?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[-\\(\\[]\\s*\\d{4}\\s*remaster(ed)?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[-\\(\\[]\\s*deluxe\\s*(edition)?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[-\\(\\[]\\s*anniversary\\s*(edition)?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[-\\(\\[]\\s*live(\\s+from\\s+[^\\)\\]]+)?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[\\(\\[]\\s*(feat|ft)\\.?\\s+[^\\)\\]]+[\\)\\]]"), "")
                .replace(Regex("(?i)\\b(feat|ft)\\.?\\s+.*$"), "")
                .replace(Regex("(?i)\\b(back\\s+to\\s+the\\s+beginning|original\\s+motion\\s+picture\\s+soundtrack|original\\s+soundtrack|ost)\\b"), "")
                .trim()

            val cleanAlbum = info.album
                .replace(Regex("(?i)[-\\(\\[]\\s*remaster(ed)?\\s*(\\d{4})?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[-\\(\\[]\\s*\\d{4}\\s*remaster(ed)?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[-\\(\\[]\\s*deluxe\\s*(edition)?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[-\\(\\[]\\s*anniversary\\s*(edition)?\\s*[\\)\\]]?"), "")
                .replace(Regex("(?i)[-\\(\\[]\\s*live(\\s+from\\s+[^\\)\\]]+)?\\s*[\\)\\]]?"), "")
                .trim()

            val targetQueries = mutableListOf<String>()
            if (cleanAlbum.isNotEmpty() && cleanAlbum.lowercase() != cleanTitle.lowercase()) {
                targetQueries.add("$primaryArtist $cleanTitle $cleanAlbum")
            }
            targetQueries.add("$primaryArtist $cleanTitle")
            if (info.title != cleanTitle) {
                targetQueries.add("$primaryArtist ${info.title}")
            }
            val cyrArtist = transliterateToCyrillic(primaryArtist)
            val cyrTitle = transliterateToCyrillic(info.title)
            if (cyrArtist != primaryArtist || cyrTitle != info.title) {
                targetQueries.add("$cyrArtist $cyrTitle")
            }

            val scoredCandidates = mutableListOf<Pair<CandidateV2, Int>>()
            val seenCandidateIds = mutableSetOf<String>()

            val executor = java.util.concurrent.Executors.newFixedThreadPool(targetQueries.size.coerceAtLeast(1))
            val futures = targetQueries.map { q ->
                executor.submit(java.util.concurrent.Callable {
                    searchYTM(q, info, isIsrcMatch = false)
                })
            }

            for (f in futures) {
                try {
                    val candidates = f.get(3, java.util.concurrent.TimeUnit.SECONDS)
                    for (c in candidates) {
                        if (seenCandidateIds.contains(c.id)) continue
                        seenCandidateIds.add(c.id)

                        val score = scoreCandidate(c, info)
                        scoredCandidates.add(Pair(c, score))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Parallel search query error: ${e.message}")
                }
            }
            executor.shutdown()

            // 3.1. Standard YouTube WEB fallback if YTM yielded no high-confidence official studio candidate (>= 7000)
            if (scoredCandidates.none { it.second >= 7000 }) {
                val stdQueries = listOf(
                    if (primaryArtist.isNotEmpty()) "$primaryArtist ${info.title} Official Audio" else "${info.title} Official Audio",
                    if (primaryArtist.isNotEmpty()) "$primaryArtist ${info.title}" else info.title
                )
                for (stdQuery in stdQueries) {
                    val stdCandidates = searchYTStandard(stdQuery, info)
                    for (c in stdCandidates) {
                        if (seenCandidateIds.contains(c.id)) continue
                        seenCandidateIds.add(c.id)

                        val score = scoreCandidate(c, info)
                        scoredCandidates.add(Pair(c, score))
                    }
                }
            }

            scoredCandidates.sortByDescending { it.second }
            val topScore = scoredCandidates.firstOrNull()?.second ?: 0
            val validCandidates = if (topScore >= 7000) {
                scoredCandidates.filter { it.second >= 7000 }
            } else {
                scoredCandidates.filter { it.second >= 100 }
            }

            if (validCandidates.isEmpty()) {
                sendTelemetry(
                    type = "ERROR",
                    trackId = info.trackId,
                    message = "No candidates reached positive confidence threshold (score >= 100)"
                )
                return rawUri
            }

            var winnerCandidate: CandidateV2? = null
            var winnerUrl: String? = null
            var winnerScore = 0
            val startTime = System.currentTimeMillis()

            for ((cand, score) in validCandidates.take(5)) {
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
                    message = "All top candidates failed direct extraction. Falling back to server proxy..."
                )
                Log.w(TAG, "Direct client extraction failed for all candidates of track ${info.trackId}. Retrying via server proxy URL!")
                return rawUri
            }

            sendTelemetry(
                type = "WINNER_SELECTED",
                trackId = info.trackId,
                candidateId = winnerCandidate.id,
                title = winnerCandidate.title,
                score = winnerScore
            )

            // 5. Send SUCCESS Telemetry with full score and ISRC
            sendTelemetry(
                type = "SUCCESS",
                trackId = info.trackId,
                candidateId = winnerCandidate.id,
                streamUrl = winnerUrl,
                duration = info.duration,
                score = winnerScore,
                isrc = info.isrc,
                artist = info.artist,
                title = winnerCandidate.title,
                source = "Client YTM WEB_REMIX",
                elapsedTimeMs = elapsed
            )

            directStreamCache[info.trackId] = winnerUrl
            uriToTrackIdMap[winnerUrl] = info.trackId.toString()
            val proxyUrl = "$BACKEND_BASE_URL/stream/${info.trackId}"
            uriToTrackIdMap[proxyUrl] = info.trackId.toString()
            resolvedCache.put(info.trackId, Pair(winnerUrl, System.currentTimeMillis()))
            Log.i(TAG, "Successfully resolved direct stream for track ${info.trackId} -> $winnerUrl")
            return Uri.parse(winnerUrl)

        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream URL for $rawUri", e)
            return rawUri
        }
    }

    private fun fetchTrackInfo(trackIdStr: String): TrackResolveInfo? {
        val cached = resolveInfoCache[trackIdStr]
        if (cached != null) return cached

        val url = "$BACKEND_BASE_URL/api/v1/tracks/${URLEncoder.encode(trackIdStr, "UTF-8")}/resolve_info"
        val req = Request.Builder().url(url).get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val info = TrackResolveInfo(
                trackId = json.optLong("track_id"),
                sourceId = json.optString("source_id"),
                isrc = json.optString("isrc"),
                title = json.optString("title"),
                artist = json.optString("artist"),
                album = json.optString("album"),
                duration = json.optInt("duration")
            )
            resolveInfoCache[trackIdStr] = info
            return info
        }
    }

    fun sendTelemetryDirect(
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
        sendTelemetry(type, trackId, sourceId, isrc, artist, title, query, candidateId, candidateDur, uploader, score, official, streamUrl, duration, source, elapsedTimeMs, message)
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

            httpClient.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.w(TAG, "Telemetry send failed: ${e.message}")
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                }
            })

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

    private fun searchYTM(query: String, info: TrackResolveInfo, isIsrcMatch: Boolean = false): List<CandidateV2> {
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
            put("params", "Eg-KAQwIABAAGAEgASgB")
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
                                    } else if (t.isNotEmpty() && t != " • " && t != "Song" && t != "Песня" && t != "Video" && t != "Видео" && t != "Track" && t != "Трек") {
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
                                        uploader = uploader,
                                        duration = cardDur,
                                        isOfficialSong = isOfficial,
                                        source = "YouTube Music Card (Top Result)",
                                        isIsrcMatch = isIsrcMatch
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
                                        vid = run0.optJSONObject("navigationEndpoint")
                                            ?.optJSONObject("watchEndpoint")
                                            ?.optString("videoId", "") ?: ""
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

                            if (flex != null && flex.length() > 1) {
                                val flex1 = flex.optJSONObject(1)
                                val textObj = flex1?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                                val runs = textObj?.optJSONArray("runs")
                                if (runs != null) {
                                    for (i in 0 until runs.length()) {
                                        val r = runs.optJSONObject(i)
                                        val t = r?.optString("text", "")?.trim() ?: ""
                                        val pageType = r?.optJSONObject("navigationEndpoint")
                                            ?.optJSONObject("browseEndpoint")
                                            ?.optJSONObject("browseEndpointContextSupportedConfigs")
                                            ?.optJSONObject("browseEndpointContextMusicConfig")
                                            ?.optString("pageType", "") ?: ""
                                        if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || (uploader.isEmpty() && t.isNotEmpty() && t !in listOf("•", "Song", "Песня", "Track", "Трек", "Композиция", "Video", "Видео", "Single", "Album", "EP", "Explicit", "E"))) {
                                            if (uploader.isEmpty() || pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                                                uploader = t
                                            }
                                        }
                                        if (t in listOf("Song", "Песня", "Track", "Трек", "Композиция")) {
                                            isOfficial = true
                                        }
                                        if (t.contains(":") && t.any { it.isDigit() }) {
                                            val d = parseDurationSec(t)
                                            if (d > 0) candDur = d
                                        }
                                    }
                                }
                            }

                            val fixed = item.optJSONArray("fixedColumns")
                            if (fixed != null && fixed.length() > 0 && candDur <= 0.0) {
                                val fixed0 = fixed.optJSONObject(0)
                                val fTextObj = fixed0?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
                                val fRuns = fTextObj?.optJSONArray("runs")
                                val fText = fRuns?.optJSONObject(0)?.optString("text", "")?.trim() ?: ""
                                if (fText.contains(":")) {
                                    val d = parseDurationSec(fText)
                                    if (d > 0) candDur = d
                                }
                            }

                            val itemStr = item.toString()
                            if (item.has("badges") || itemStr.contains("MUSIC_VIDEO_TYPE_ATV") || itemStr.contains("- Topic") || uploader.contains("- Topic") || (uploader.isNotEmpty() && uploader.equals(info.artist, ignoreCase = true))) {
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
                                        source = "YouTube Music (WEB_REMIX)",
                                        isIsrcMatch = isIsrcMatch
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

    private fun searchYTStandard(query: String, info: TrackResolveInfo): List<CandidateV2> {
        val url = "https://www.youtube.com/youtubei/v1/search"
        val reqBodyJson = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240101.00.00")
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

        try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val json = JSONObject(body)

                fun parse(obj: Any) {
                    when (obj) {
                        is JSONObject -> {
                            if (obj.has("videoRenderer")) {
                                val vr = obj.getJSONObject("videoRenderer")
                                val vid = vr.optString("videoId", "")
                                val titleRuns = vr.optJSONObject("title")?.optJSONArray("runs")
                                val title = titleRuns?.optJSONObject(0)?.optString("text", "") ?: ""
                                val ownerRuns = vr.optJSONObject("ownerText")?.optJSONArray("runs")
                                val uploader = ownerRuns?.optJSONObject(0)?.optString("text", "") ?: ""
                                val lengthText = vr.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""
                                val dur = parseDurationSec(lengthText)

                                if (vid.isNotEmpty() && title.isNotEmpty() && !seenIds.contains(vid)) {
                                    seenIds.add(vid)
                                    candidates.add(
                                        CandidateV2(
                                            id = vid,
                                            title = title,
                                            uploader = uploader,
                                            duration = dur,
                                            isOfficialSong = uploader.contains("VEVO") || uploader.contains("Official") || uploader.lowercase() == info.artist.lowercase(),
                                            source = "YouTube Standard WEB"
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
        } catch (e: Exception) {
            Log.w(TAG, "Standard YT search failed for '$query'", e)
        }
        return candidates
    }

    private fun scoreCandidate(c: CandidateV2, info: TrackResolveInfo): Int {
        val targetTitle = info.title.lowercase().trim()
        val targetArtist = info.artist.lowercase().trim()
        val candTitle = c.title.lowercase().trim()
        var score = 100

        // Support multi-artists separated by ';' or ','
        val artistList = info.artist.split(";").flatMap { it.split(",") }.map { cleanNormalize(it) }.filter { it.isNotEmpty() }
        val primaryArtistClean = if (artistList.isNotEmpty()) artistList[0] else ""
        val cUploaderClean = cleanNormalize(c.uploader)
        val cTitleClean = cleanNormalize(c.title)
        val tTitleClean = cleanNormalize(info.title)

        val targetCore = extractCoreTitle(info.title, primaryArtistClean)
        val candCore = extractCoreTitle(c.title, primaryArtistClean)

        val tCoreLat = transliterateToLatin(targetCore)
        val cCoreLat = transliterateToLatin(candCore)
        val tTitleLat = transliterateToLatin(tTitleClean)
        val cTitleLat = transliterateToLatin(cTitleClean)

        val tCanon = toCanonical(info.title)
        val cCanon = toCanonical(c.title)
        val tCoreCanon = toCanonical(targetCore)
        val cCoreCanon = toCanonical(candCore)

        val titleMatches = (cTitleClean == tTitleClean) ||
                (tTitleLat == cTitleLat) ||
                (cCanon == tCanon) ||
                (tCoreCanon.isNotEmpty() && cCoreCanon.isNotEmpty() && tCoreCanon == cCoreCanon) ||
                (cCanon.contains(tCoreCanon) && tCoreCanon.length >= 3) ||
                (tCanon.contains(cCoreCanon) && cCoreCanon.length >= 3) ||
                (targetCore.isNotEmpty() && candCore.isNotEmpty() && (targetCore == candCore || tCoreLat == cCoreLat)) ||
                (cTitleClean.contains(targetCore) && targetCore.length >= 3) ||
                (tTitleClean.contains(candCore) && candCore.length >= 3) ||
                (cTitleLat.contains(tCoreLat) && tCoreLat.length >= 3) ||
                (tTitleLat.contains(cCoreLat) && cCoreLat.length >= 3)

        val artCanons = artistList.map { toCanonical(it) }
        val uploaderCanon = toCanonical(cUploaderClean)

        val inTitlePerformer = (targetArtist.isNotEmpty() && candTitle.contains(targetArtist)) ||
                (primaryArtistClean.isNotEmpty() && candTitle.contains(primaryArtistClean)) ||
                artCanons.any { art -> art.isNotEmpty() && cCanon.contains(art) }

        val artistMatches = artistList.any { art ->
            val artLat = transliterateToLatin(art)
            val uploaderLat = transliterateToLatin(cUploaderClean)
            val artCan = toCanonical(art)
            art.isNotEmpty() && (
                cUploaderClean == art || uploaderLat == artLat || uploaderCanon == artCan ||
                cUploaderClean == "$art topic" || uploaderLat == "$artLat topic" || uploaderCanon == "$artCan topic" || uploaderCanon.startsWith("$artCan ") ||
                cUploaderClean == "$art vevo" || uploaderLat == "$artLat vevo" ||
                cUploaderClean == "$art official" || uploaderLat == "$artLat official" ||
                (cUploaderClean.startsWith("$art ") && (cUploaderClean.contains("topic") || cUploaderClean.contains("vevo") || cUploaderClean.contains("official"))) ||
                (uploaderLat.startsWith("$artLat ") && (uploaderLat.contains("topic") || uploaderLat.contains("vevo") || uploaderLat.contains("official"))) ||
                (uploaderCanon.startsWith("$artCan ") && (uploaderCanon.contains("topic") || uploaderCanon.contains("vevo") || uploaderCanon.contains("official"))) ||
                cTitleClean.startsWith("$art ") || cTitleLat.startsWith("$artLat ") || cCanon.startsWith("$artCan ") ||
                cTitleClean.endsWith(" $art") || cTitleLat.endsWith(" $artLat") || cCanon.endsWith(" $artCan") ||
                cTitleClean.contains(" $art ") || cTitleLat.contains(" $artLat ") || cCanon.contains(" $artCan ") ||
                cTitleClean == art || cTitleLat == artLat || cCanon == artCan
            )
        }

        // 🔥 1. ISRC MATCH DIRECT BONUS: Award +2000 ONLY if title AND artist match target!
        if (c.isIsrcMatch) {
            val isMatch = titleMatches && (artistMatches || inTitlePerformer)
            if (isMatch) {
                score += 2000
            } else if (!artistMatches && !inTitlePerformer && primaryArtistClean.isNotEmpty()) {
                return -1500 // Reject mismatched ISRC result where artist is completely different
            }
        }

        // Soft penalty for cover/ensemble keywords ONLY when candidate artist does NOT match target artist
        val coverBandKeywords = listOf(
            "quartet", "string quartet", "orchestral cover", "tribute band", "lullaby",
            "chamber cover", "symphonic cover", "8-bit", "arcade cover"
        )
        for (ck in coverBandKeywords) {
            if (cUploaderClean.contains(ck) && !artistMatches && !targetTitle.contains(ck)) {
                score -= 1500 // Penalize mismatched cover ensemble, but leave real bands intact
            }
        }

        // Severe penalty for unwanted non-music content
        if (candTitle.contains("bass only") || candTitle.contains("drums only") || candTitle.contains("vocal only") ||
            candTitle.contains("isolated") || candTitle.contains("backing track") || candTitle.contains("karaoke") ||
            candTitle.contains("reaction") || candTitle.contains("review") ||
            candTitle.contains("tutorial") || candTitle.contains("lesson") || candTitle.contains("tab")) {
            return -2000 // Hard rejection so non-music content can NEVER win
        }

        // Hard non-music / parody / reaction penalty
        val parodyOrReactionKeywords = listOf(
            "reaction", "review", "tutorial", "lesson", "tab", "performed in",
            "parody", "metal trump", "isolated", "backing track", "karaoke"
        )
        if (parodyOrReactionKeywords.any { candTitle.contains(it) || cUploaderClean.contains(it) }) {
            return -10000 // Hard rejection for parody/reaction/tutorial
        }

        // Hard live track penalty: if target does NOT ask for live, penalize live recordings!
        val liveKeywords = listOf(
            "live", "concert", "live at", "live in", "rock in rio", "download fest", "wacken",
            "woodstock", "tokyo dome", "tour", "graspop", "hellfest", "rock am ring", "rock am",
            "en vivo", "ao vivo", "arena", "stadium", "festival", "live 19", "live 20", "live 2k"
        )
        val inTargetLive = liveKeywords.any { targetTitle.contains(it) }
        val inCandLive = liveKeywords.any { candTitle.contains(it) || cUploaderClean.contains(it) }
        if (!inTargetLive && inCandLive) {
            score -= 6000
        }

        // Hard acoustic penalty
        val inTargetAcoustic = targetTitle.contains("acoustic") || targetTitle.contains("unplugged")
        val inCandAcoustic = candTitle.contains("acoustic") || candTitle.contains("unplugged") || candTitle.contains("arrangement")
        if (!inTargetAcoustic && inCandAcoustic) {
            score -= 2500
        }

        // Hard revisited penalty: if target does NOT ask for revisited, penalize revisited versions!
        val inTargetRevisited = targetTitle.contains("revisited")
        val inCandRevisited = candTitle.contains("revisited")
        if (!inTargetRevisited && inCandRevisited) {
            score -= 2500
        }

        // Hard remix penalty: if target does NOT ask for remix, penalize remixes severely!
        val inTargetRemix = targetTitle.contains("remix")
        val inCandRemix = candTitle.contains("remix")
        if (!inTargetRemix && inCandRemix) {
            score -= 3000
        }

        // Hard unofficial penalty
        if (candTitle.contains("unofficial")) {
            score -= 3000
        }

        // Bypass version keyword penalties for exact ISRC matches
        if (!c.isIsrcMatch) {
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

            val moderateVersionKeywords = listOf("remix", "radio edit", "deluxe", "remaster", "extended", "slowed", "reverb", "nightcore", "8-bit", "revisited")
            for (vk in moderateVersionKeywords) {
                val inTarget = targetTitle.contains(vk)
                val inCand = candTitle.contains(vk) || cUploaderClean.contains(vk)
                if (inTarget && inCand) score += 400
                else if (inTarget && !inCand && !c.isOfficialSong) score -= 400
                else if (!inTarget && inCand) score -= 500
            }
        }

        // Hard cover penalty
        if (!targetTitle.contains("cover") && candTitle.contains("cover")) {
            score -= 2500
        }

        // Penalize unrequested features ONLY if candidate is not an official release
        if (!c.isOfficialSong && !targetTitle.contains("feat") && !targetTitle.contains("ft.") &&
            (candTitle.contains("feat.") || candTitle.contains("feat ") || candTitle.contains("ft."))) {
            score -= 1500
        }

        if (!titleMatches && !cTitleClean.contains(tTitleClean) && !tTitleClean.contains(cTitleClean) && !cCanon.contains(tCanon) && !tCanon.contains(cCanon)) {
            return -10000 // Hard rejection if title does not match (instant disqualification)
        }

        // Hard rejection if candidate artist is completely different from target
        if (!artistMatches && !inTitlePerformer && primaryArtistClean.isNotEmpty()) {
            return -10000
        }

        // 🔥 Official song classification (YouTube Music verified album release)
        if (c.isOfficialSong) {
            score += 10000
        } else {
            score -= 2000
        }

        if (artistMatches) {
            score += 1500
            if (cUploaderClean.contains("topic") || uploaderCanon.contains("topic")) {
                score += 2000
            }
        } else if (inTitlePerformer) {
            score += 1000
        }

        if (candTitle == targetTitle || cCanon == tCanon) score += 1000
        else if (titleMatches) score += 500

        // Continuous duration scoring curve
        val targetDur = info.duration.toDouble()
        if (targetDur > 0 && c.duration > 0) {
            val diff = Math.abs(targetDur - c.duration)
            if (diff <= 3.0) {
                score += 1000
            } else if (diff <= 8.0) {
                score += 400
            } else if (diff > 30.0) {
                return -10000
            } else if (diff > 18.0) {
                score -= 3000
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
        val subParts = t.split(Regex("\\(|\\[|\\bfeat\\b|\\blive\\b|\\bremix\\b", RegexOption.IGNORE_CASE))
        return cleanNormalize(if (subParts.isNotEmpty()) subParts[0] else t)
    }

    private fun extractPlayerStreamUrl(videoId: String, targetDurationSec: Int = 0): String? {
        val sts = currentSignatureTimestamp

        val profiles = listOf(
            ExtractorProfile(
                name = "VISIONOS",
                version = "1.02",
                uaPlayer = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                uaDownload = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                deviceMake = "Apple",
                deviceModel = "RealityDevice17,1",
                osName = "visionOS",
                osVersion = "26.5.23O471",
                clientNameHeader = "101"
            ),
            ExtractorProfile(
                name = "WEB_REMIX",
                version = "1.20241021.01.00",
                uaPlayer = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
                uaDownload = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
            ),
            ExtractorProfile(
                name = "ANDROID_VR",
                version = "1.65.10",
                uaPlayer = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                uaDownload = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1)",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                sdkVersion = 32,
                osName = "Android",
                osVersion = "12L"
            ),
            ExtractorProfile(
                name = "IOS",
                version = "19.29.1",
                uaPlayer = "com.google.ios.youtube/19.29.1 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X; en_US)",
                uaDownload = "com.google.ios.youtube/19.29.1 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X; en_US)",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iOS",
                osVersion = "17.5.1"
            )
        )

        for (prof in profiles) {
            try {
                val vData = getOrFetchVisitorData(prof)
                val url = "https://www.youtube.com/youtubei/v1/player"
                val clientJson = JSONObject().apply {
                    put("clientName", prof.name)
                    put("clientVersion", prof.version)
                    put("hl", "en")
                    put("gl", "US")
                    if (prof.deviceMake.isNotEmpty()) {
                        put("deviceMake", prof.deviceMake)
                        put("deviceModel", prof.deviceModel)
                    }
                    if (prof.sdkVersion > 0) {
                        put("androidSdkVersion", prof.sdkVersion)
                    }
                    if (prof.osName.isNotEmpty()) {
                        put("osName", prof.osName)
                        put("osVersion", prof.osVersion)
                    }
                    if (vData.isNotEmpty()) {
                        put("visitorData", vData)
                    }
                }

                val poToken = if (vData.isNotEmpty()) PoTokenProvider.generatePoToken(vData) else PoTokenProvider.generatePoToken(videoId)
                val reqBodyJson = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", clientJson)
                    })
                    if (poToken.isNotEmpty()) {
                        put("serviceIntegrityDimensions", JSONObject().apply {
                            put("poToken", poToken)
                        })
                    }
                    put("videoId", videoId)
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("html5Preference", "HTML5_PREF_WANTS")
                            put("signatureTimestamp", sts)
                        })
                    })
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", prof.uaPlayer)
                if (prof.clientNameHeader.isNotEmpty()) {
                    reqBuilder.header("X-YouTube-Client-Name", prof.clientNameHeader)
                    reqBuilder.header("X-YouTube-Client-Version", prof.version)
                }
                if (vData.isNotEmpty()) {
                    reqBuilder.header("X-Goog-Visitor-Id", vData)
                }
                val req = reqBuilder
                    .post(reqBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "HTTP Error ${resp.code} for profile ${prof.name}"
                        Log.w(TAG, "Extraction failed for $videoId: $msg")
                        org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXTRACT_FAIL", "VideoID $videoId | $msg")
                        return@use
                    }
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val playability = json.optJSONObject("playabilityStatus")
                    val status = playability?.optString("status", "") ?: ""
                    if (status != "OK") {
                        val reason = playability?.optString("reason", "") ?: "Status: $status"
                        Log.w(TAG, "Profile ${prof.name} unplayable for $videoId: $status - $reason")
                        org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXTRACT_FAIL", "VideoID $videoId | Profile ${prof.name} Status: $status | Reason: '$reason'")
                        return@use
                    }

                    val videoDetails = json.optJSONObject("videoDetails")
                    val playerLengthSec = videoDetails?.optString("lengthSeconds", "0")?.toIntOrNull() ?: 0
                    if (targetDurationSec > 0 && playerLengthSec > 0) {
                        val diff = Math.abs(targetDurationSec - playerLengthSec)
                        if (diff > 35 || (playerLengthSec < 0.5 * targetDurationSec && diff > 20)) {
                            val msg = "Duration mismatch: Video ${playerLengthSec}s vs Target ${targetDurationSec}s (diff ${diff}s > 35s)"
                            Log.w(TAG, "Rejected candidate $videoId: $msg")
                            org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXTRACT_FAIL", "VideoID $videoId | $msg")
                            return null
                        }
                    }

                    val streamingData = json.optJSONObject("streamingData")
                    if (streamingData == null) {
                        Log.w(TAG, "No streamingData for $videoId using profile ${prof.name}")
                        org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXTRACT_FAIL", "VideoID $videoId | Profile ${prof.name}: No streamingData")
                        return@use
                    }

                    val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats == null || adaptiveFormats.length() == 0) {
                        Log.w(TAG, "No adaptiveFormats for $videoId using profile ${prof.name}")
                        org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXTRACT_FAIL", "VideoID $videoId | Profile ${prof.name}: Empty adaptiveFormats")
                        return@use
                    }

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
                                streamUrl = unlockCipherViaServer(cipher)
                            }
                            if (streamUrl.isNotEmpty()) {
                                val bitrate = fmt.optInt("bitrate", 0)
                                val isAAC = itag == 140 || mimeType.contains("audio/mp4") || mimeType.contains("audio/m4a")
                                val isOpus160k = itag == 251 || (mimeType.contains("audio/webm") && bitrate > 120000)
                                val formatScore = bitrate + (if (isAAC) 2000000 else if (isOpus160k) 1000000 else 0)
                                if (formatScore > bestScore) {
                                    bestScore = formatScore
                                    bestUrl = streamUrl
                                }
                            }
                        }
                    }

                    if (!bestUrl.isNullOrEmpty()) {
                        var finalUrl = bestUrl
                        if (!finalUrl.contains("ratebypass=yes")) {
                            finalUrl = if (finalUrl.contains("?")) "$finalUrl&ratebypass=yes" else "$finalUrl?ratebypass=yes"
                        }
                        urlToUserAgentMap[finalUrl] = prof.uaDownload
                        Log.i(TAG, "Successfully extracted direct audio URL using profile ${prof.name} for VideoID $videoId")
                        org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXTRACT_SUCCESS", "VideoID $videoId | Profile: ${prof.name} | Quality score: $bestScore")
                        return finalUrl
                    } else {
                        Log.w(TAG, "No direct audio URL found for $videoId using profile ${prof.name}")
                        org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXTRACT_FAIL", "VideoID $videoId | Profile ${prof.name}: No audio stream URL")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed profile ${prof.name} for VideoID $videoId: ${e.message}")
                org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXTRACT_FAIL", "VideoID $videoId | Profile ${prof.name} Exception: ${e.message}")
            }
        }
        return null
    }

    private fun unlockCipherViaServer(cipher: String): String {
        try {
            val reqJson = JSONObject().apply {
                put("cipher", cipher)
            }
            val req = Request.Builder()
                .url("$BACKEND_BASE_URL/api/v1/decipher_cipher")
                .post(reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val resUrl = json.optString("url", "")
                        if (resUrl.isNotEmpty()) return resUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decipher cipher via server: ${e.message}")
        }
        val params = cipher.split("&")
        for (p in params) {
            if (p.startsWith("url=")) {
                return java.net.URLDecoder.decode(p.substring(4), "UTF-8")
            }
        }
        return ""
    }

    private fun unlockStreamUrlViaServer(rawUrl: String): String {
        var processedUrl = rawUrl
        if (processedUrl.contains("googlevideo.com") && processedUrl.contains("&n=")) {
            try {
                val reqJson = JSONObject().apply {
                    put("url", processedUrl)
                }
                val req = Request.Builder()
                    .url("$BACKEND_BASE_URL/api/v1/decipher_url")
                    .post(reqJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            processedUrl = json.optString("url", processedUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decipher n-param via server: ${e.message}")
            }
        }

        // 🔥 GUARANTEED RATEBYPASS: Always append ratebypass=yes to all googlevideo URLs
        if (processedUrl.contains("googlevideo.com") && !processedUrl.contains("ratebypass=yes")) {
            processedUrl = if (processedUrl.contains("?")) "$processedUrl&ratebypass=yes" else "$processedUrl?ratebypass=yes"
        }
        return processedUrl
    }

    private data class DeezerIsrcMeta(
        val artist: String,
        val title: String,
        val album: String,
        val duration: Double
    )

    private fun resolveDeezerIsrc(isrc: String): DeezerIsrcMeta? {
        try {
            val url = java.net.URL("https://api.deezer.com/track/isrc:$isrc")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = org.json.JSONObject(json)
                if (obj.has("title")) {
                    val title = obj.optString("title", "")
                    val artist = obj.optJSONObject("artist")?.optString("name", "") ?: ""
                    val album = obj.optJSONObject("album")?.optString("title", "") ?: ""
                    val duration = obj.optDouble("duration", 0.0)
                    return DeezerIsrcMeta(artist, title, album, duration)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deezer ISRC resolve failed for $isrc: ${e.message}")
        }
        return null
    }

    private fun transliterateToLatin(input: String): String {
        val clean = input.lowercase().trim()
        val sb = StringBuilder()
        for (ch in clean) {
            when (ch) {
                'а' -> sb.append('a')
                'б' -> sb.append('b')
                'в' -> sb.append('v')
                'г' -> sb.append('g')
                'д' -> sb.append('d')
                'е', 'э' -> sb.append('e')
                'ё' -> sb.append("yo")
                'ж' -> sb.append("zh")
                'з' -> sb.append('z')
                'и' -> sb.append('i')
                'й' -> sb.append('y')
                'к' -> sb.append('k')
                'л' -> sb.append('l')
                'м' -> sb.append('m')
                'н' -> sb.append('n')
                'о' -> sb.append('o')
                'п' -> sb.append('p')
                'р' -> sb.append('r')
                'с' -> sb.append('s')
                'т' -> sb.append('t')
                'у' -> sb.append('u')
                'ф' -> sb.append('f')
                'х' -> sb.append("kh")
                'ц' -> sb.append("ts")
                'ч' -> sb.append("ch")
                'ш' -> sb.append("sh")
                'щ' -> sb.append("shch")
                'ъ', 'ь' -> {}
                'ы' -> sb.append('y')
                'ю' -> sb.append("yu")
                'я' -> sb.append("ya")
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace("yy", "y").replace("ij", "i").replace("iy", "i")
    }

    private fun transliterateToCyrillic(input: String): String {
        var s = input.lowercase().trim()
        val pairs = listOf(
            "shch" to "щ", "yo" to "ё", "zh" to "ж", "kh" to "х",
            "ts" to "ц", "ch" to "ч", "sh" to "ш", "yu" to "ю", "ya" to "я",
            "aria" to "ария", "bespechnyy" to "беспечный", "bespechnyy angel" to "беспечный ангел",
            "angel" to "ангел", "shtil" to "штиль"
        )
        for ((lat, cyr) in pairs) {
            s = s.replace(lat, cyr)
        }
        val singleMap = mapOf(
            'a' to 'а', 'b' to 'б', 'v' to 'в', 'g' to 'г', 'd' to 'д',
            'e' to 'е', 'z' to 'з', 'i' to 'и', 'k' to 'к', 'l' to 'л',
            'm' to 'м', 'n' to 'н', 'o' to 'о', 'p' to 'п', 'r' to 'р',
            's' to 'с', 't' to 'т', 'u' to 'у', 'f' to 'ф', 'y' to 'ы'
        )
        val sb = StringBuilder()
        for (ch in s) {
            sb.append(singleMap[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun toCanonical(input: String): String {
        var s = input.lowercase().trim()
        s = s.replace('ё', 'е').replace('й', 'и').replace('ы', 'и').replace('ь', ' ').replace('ъ', ' ')

        val digraphs = listOf(
            "shch" to "щ", "sch" to "щ",
            "yo" to "е", "jo" to "е", "ye" to "е", "je" to "е",
            "zh" to "ж",
            "ch" to "ч",
            "sh" to "ш",
            "cz" to "ц", "ts" to "ц", "tc" to "ц",
            "yu" to "ю", "ju" to "ю",
            "ya" to "я", "ja" to "я", "ia" to "я",
            "yy" to "и", "yj" to "и", "iy" to "и", "ij" to "и", "ey" to "е",
            "kh" to "х",
            "ph" to "ф"
        )
        for ((lat, cyr) in digraphs) {
            s = s.replace(lat, cyr)
        }

        val singleLetters = listOf(
            'a' to 'а', 'b' to 'б', 'v' to 'в', 'w' to 'в', 'g' to 'г',
            'd' to 'д', 'e' to 'е', 'z' to 'з', 'i' to 'и', 'j' to 'и',
            'k' to 'к', 'l' to 'л', 'm' to 'м', 'n' to 'н', 'o' to 'о',
            'p' to 'п', 'r' to 'р', 's' to 'с', 't' to 'т', 'u' to 'у',
            'f' to 'ф', 'h' to 'х', 'c' to 'к', 'y' to 'и', 'x' to 'к',
            'q' to 'к'
        )
        val sb = StringBuilder()
        for (ch in s) {
            val mapped = singleLetters.find { it.first == ch }?.second ?: ch
            sb.append(mapped)
        }
        s = sb.toString()

        s = s.replace('ё', 'е').replace('й', 'и').replace('ы', 'и').replace('ь', ' ').replace('ъ', ' ')
        s = s.replace("я", "ия").replace("ю", "иу")
        s = s.replace(Regex("[^a-z0-9а-я]"), " ")
        s = s.replace(Regex("([а-яa-z])\\1+"), "$1")
        return s.replace(Regex("\\s+"), " ").trim()
    }
}
