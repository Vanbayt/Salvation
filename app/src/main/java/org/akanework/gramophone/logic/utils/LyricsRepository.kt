package org.akanework.gramophone.logic.utils

import android.content.Context
import androidx.media3.common.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class LyricsSource(val displayName: String) {
    ALL("Все агрегаторы"),
    LRCLIB("LRCLIB"),
    NETEASE("NetEase Music"),
    KUGOU("Kugou Music")
}

data class LyricsResult(
    val lyrics: SemanticLyrics?,
    val sourceName: String,
    val isSynced: Boolean,
    val isInstrumental: Boolean = false
)

object LyricsRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun sanitizeTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("(?i)\\s*[-\\(\\[]?\\s*\\d{4}\\s*(digital|stereo|mono)?\\s*remaster(ed)?\\s*[\\]\\)]?"), "")
            .replace(Regex("(?i)\\s*[-\\(\\[]?\\s*remaster(ed)?\\s*[\\]\\)]?"), "")
            .replace(Regex("(?i)\\s*[-\\(\\[]?\\s*(deluxe|edition|version|bonus track|official|audio|video|lyric video|hd|4k)\\s*[\\]\\)]?"), "")
            .replace(Regex("(?i)\\s*[-\\(\\[]?\\s*(feat|ft)\\..*[\\]\\)]?"), "")
            .replace(Regex("[\\(\\)\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun sanitizeArtist(rawArtist: String): String {
        return rawArtist
            .replace(Regex("(?i)\\s*(feat|ft)\\..*"), "")
            .replace(Regex("[\\(\\)\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun fetchLyrics(
        context: Context,
        file: File?,
        mimeType: String?,
        sampleRate: Int,
        metadata: Metadata?,
        artist: String?,
        title: String?,
        durationMs: Long,
        preferredSource: LyricsSource = LyricsSource.ALL,
        options: LrcUtils.LrcParserOptions
    ): LyricsResult? = withContext(Dispatchers.IO) {

        val rawTitle = title?.trim() ?: ""
        val rawArtist = artist?.trim() ?: ""
        val cleanTitle = sanitizeTitle(rawTitle)
        val cleanArtist = sanitizeArtist(rawArtist)

        if (cleanTitle.isEmpty() && cleanArtist.isEmpty()) {
            return@withContext null
        }

        val titlesToTry = listOfNotNull(cleanTitle, rawTitle).distinct()
        val artistsToTry = listOfNotNull(cleanArtist, rawArtist).distinct()

        var result: LyricsResult? = null

        // 1. Try LRCLIB
        if (preferredSource == LyricsSource.ALL || preferredSource == LyricsSource.LRCLIB) {
            for (t in titlesToTry) {
                for (a in artistsToTry) {
                    val lrclibResult = fetchFromLrclib(a, t, durationMs, options)
                    if (lrclibResult != null) {
                        result = lrclibResult
                        break
                    }
                }
                if (result != null) break
            }
        }

        // 2. Try NetEase Music
        if (result == null && (preferredSource == LyricsSource.ALL || preferredSource == LyricsSource.NETEASE)) {
            for (t in titlesToTry) {
                for (a in artistsToTry) {
                    val neteaseResult = fetchFromNetease(a, t, options)
                    if (neteaseResult != null) {
                        result = neteaseResult
                        break
                    }
                }
                if (result != null) break
            }
        }

        // 3. Try Kugou Music
        if (result == null && (preferredSource == LyricsSource.ALL || preferredSource == LyricsSource.KUGOU)) {
            for (t in titlesToTry) {
                for (a in artistsToTry) {
                    val kugouResult = fetchFromKugou(a, t, durationMs, options)
                    if (kugouResult != null) {
                        result = kugouResult
                        break
                    }
                }
                if (result != null) break
            }
        }

        if (result?.lyrics is SemanticLyrics.SyncedLyrics) {
            translateLyricsToRussianIfNeeded(result.lyrics as SemanticLyrics.SyncedLyrics)
        }

        return@withContext result
    }

    private fun translateLyricsToRussianIfNeeded(syncedLyrics: SemanticLyrics.SyncedLyrics) {
        try {
            val linesToTranslate = syncedLyrics.text.filter { it.text.isNotBlank() && it.translation.isNullOrBlank() }
            if (linesToTranslate.isEmpty()) return

            val batchText = StringBuilder()
            linesToTranslate.forEach { line ->
                batchText.append(line.text.replace("\n", " ").trim()).append("\n")
            }

            val encoded = URLEncoder.encode(batchText.toString(), "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ru&dt=t&q=$encoded"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return
                val jsonArray = JSONArray(jsonStr)
                val sentencesArray = jsonArray.optJSONArray(0) ?: return

                val translatedTextBuilder = StringBuilder()
                for (i in 0 until sentencesArray.length()) {
                    val sentence = sentencesArray.optJSONArray(i) ?: continue
                    val transSegment = sentence.optString(0, "")
                    translatedTextBuilder.append(transSegment)
                }

                val translatedLines = translatedTextBuilder.toString().split("\n")
                var transIdx = 0
                linesToTranslate.forEach { line ->
                    if (transIdx < translatedLines.size) {
                        val trans = translatedLines[transIdx].trim()
                        if (trans.isNotEmpty() && trans.lowercase() != line.text.trim().lowercase()) {
                            line.translation = trans
                        }
                        transIdx++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchFromLrclib(
        artist: String,
        title: String,
        durationMs: Long,
        options: LrcUtils.LrcParserOptions
    ): LyricsResult? {
        try {
            val durationSec = if (durationMs > 0) durationMs / 1000 else 0
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")

            var url = "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"
            if (durationSec > 0) {
                url += "&duration=$durationSec"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SalvationMusicApp/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return null
                val json = JSONObject(jsonStr)

                if (json.optBoolean("instrumental", false)) {
                    return LyricsResult(
                        lyrics = null,
                        sourceName = "LRCLIB (Инструментал)",
                        isSynced = false,
                        isInstrumental = true
                    )
                }

                val syncedLrc = json.optString("syncedLyrics", "").trim()
                val plainLrc = json.optString("plainLyrics", "").trim()

                val rawLyrics = if (syncedLrc.isNotEmpty() && syncedLrc != "null") syncedLrc else plainLrc
                if (rawLyrics.isNotEmpty() && rawLyrics != "null") {
                    val parsed = parseLrc(rawLyrics, options.trim, options.multiLine)
                    if (parsed != null) {
                        return LyricsResult(
                            lyrics = parsed,
                            sourceName = "LRCLIB",
                            isSynced = parsed is SemanticLyrics.SyncedLyrics
                        )
                    }
                }
            }

            // Search fallback for LRCLIB
            val searchUrl = "https://lrclib.net/api/search?q=${URLEncoder.encode("$artist $title", "UTF-8")}"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "SalvationMusicApp/1.0")
                .build()
            val searchResp = client.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val arrStr = searchResp.body?.string() ?: return null
                val jsonArray = JSONArray(arrStr)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val syncedLrc = item.optString("syncedLyrics", "").trim()
                    val plainLrc = item.optString("plainLyrics", "").trim()
                    val rawLyrics = if (syncedLrc.isNotEmpty() && syncedLrc != "null") syncedLrc else plainLrc
                    if (rawLyrics.isNotEmpty() && rawLyrics != "null") {
                        val parsed = parseLrc(rawLyrics, options.trim, options.multiLine)
                        if (parsed != null) {
                            return LyricsResult(
                                lyrics = parsed,
                                sourceName = "LRCLIB",
                                isSynced = parsed is SemanticLyrics.SyncedLyrics
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun fetchFromNetease(
        artist: String,
        title: String,
        options: LrcUtils.LrcParserOptions
    ): LyricsResult? {
        try {
            val query = "$artist $title".trim()
            val searchUrl = "https://music.163.com/api/search/get/web?s=${URLEncoder.encode(query, "UTF-8")}&type=1&limit=5"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://music.163.com/")
                .build()

            val searchResp = client.newCall(searchRequest).execute()
            if (!searchResp.isSuccessful) return null

            val searchJsonStr = searchResp.body?.string() ?: return null
            val searchJson = JSONObject(searchJsonStr)
            val resultObj = searchJson.optJSONObject("result") ?: return null
            val songsArray = resultObj.optJSONArray("songs") ?: return null

            for (i in 0 until songsArray.length()) {
                val songId = songsArray.getJSONObject(i).optLong("id", 0)
                if (songId == 0L) continue

                val lyricUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
                val lyricRequest = Request.Builder()
                    .url(lyricUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://music.163.com/")
                    .build()

                val lyricResp = client.newCall(lyricRequest).execute()
                if (!lyricResp.isSuccessful) continue

                val lyricJsonStr = lyricResp.body?.string() ?: continue
                val lyricJson = JSONObject(lyricJsonStr)

                val lrcObj = lyricJson.optJSONObject("lrc") ?: continue
                val lyricText = lrcObj.optString("lyric", "").trim()
                val tlyricObj = lyricJson.optJSONObject("tlyric")
                val tlyricText = tlyricObj?.optString("lyric", "")?.trim() ?: ""

                if (lyricText.isNotEmpty() && lyricText != "null") {
                    val parsed = parseLrc(lyricText, options.trim, options.multiLine)
                    if (parsed != null) {
                        if (parsed is SemanticLyrics.SyncedLyrics && tlyricText.isNotEmpty() && tlyricText != "null") {
                            val parsedTranslation = parseLrc(tlyricText, options.trim, options.multiLine)
                            if (parsedTranslation is SemanticLyrics.SyncedLyrics) {
                                val transMap = parsedTranslation.text.associateBy { it.start }
                                parsed.text.forEach { origLine ->
                                    val match = transMap[origLine.start] ?: parsedTranslation.text.find { Math.abs(it.start.toLong() - origLine.start.toLong()) < 1200L }
                                    if (match != null && match.text.isNotBlank()) {
                                        origLine.translation = match.text
                                    }
                                }
                            }
                        }

                        return LyricsResult(
                            lyrics = parsed,
                            sourceName = "NetEase Music",
                            isSynced = parsed is SemanticLyrics.SyncedLyrics
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun fetchFromKugou(
        artist: String,
        title: String,
        durationMs: Long,
        options: LrcUtils.LrcParserOptions
    ): LyricsResult? {
        try {
            val query = "$artist $title".trim()
            val searchUrl = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=${URLEncoder.encode(query, "UTF-8")}&page=1&pagesize=5"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Android")
                .build()

            val searchResp = client.newCall(searchRequest).execute()
            if (!searchResp.isSuccessful) return null

            val searchJsonStr = searchResp.body?.string() ?: return null
            val searchJson = JSONObject(searchJsonStr)
            val dataObj = searchJson.optJSONObject("data") ?: return null
            val infoArray = dataObj.optJSONArray("info") ?: return null

            for (i in 0 until infoArray.length()) {
                val songHash = infoArray.getJSONObject(i).optString("hash", "")
                if (songHash.isEmpty()) continue

                val lrcUrl = "http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=${URLEncoder.encode(query, "UTF-8")}&duration=$durationMs&hash=$songHash"
                val lrcRequest = Request.Builder()
                    .url(lrcUrl)
                    .header("User-Agent", "Android")
                    .build()

                val lrcResp = client.newCall(lrcRequest).execute()
                if (!lrcResp.isSuccessful) continue

                val lrcJsonStr = lrcResp.body?.string() ?: continue
                val lrcJson = JSONObject(lrcJsonStr)
                val candidates = lrcJson.optJSONArray("candidates") ?: continue

                for (j in 0 until candidates.length()) {
                    val candidate = candidates.getJSONObject(j)
                    val id = candidate.optString("id", "")
                    val accesskey = candidate.optString("accesskey", "")

                    if (id.isNotEmpty() && accesskey.isNotEmpty()) {
                        val downloadUrl = "http://krcs.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8"
                        val downloadReq = Request.Builder().url(downloadUrl).build()
                        val downloadResp = client.newCall(downloadReq).execute()
                        if (downloadResp.isSuccessful) {
                            val contentJsonStr = downloadResp.body?.string() ?: continue
                            val contentJson = JSONObject(contentJsonStr)
                            val base64Content = contentJson.optString("content", "")
                            if (base64Content.isNotEmpty()) {
                                val decodedBytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
                                val lrcText = String(decodedBytes, Charsets.UTF_8).trim()
                                if (lrcText.isNotEmpty() && lrcText != "null") {
                                    val parsed = parseLrc(lrcText, options.trim, options.multiLine)
                                    if (parsed != null) {
                                        return LyricsResult(
                                            lyrics = parsed,
                                            sourceName = "Kugou Music",
                                            isSynced = parsed is SemanticLyrics.SyncedLyrics
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
