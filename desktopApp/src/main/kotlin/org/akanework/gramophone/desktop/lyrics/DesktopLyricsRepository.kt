package org.akanework.gramophone.desktop.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DesktopLyricLine(
    val startMs: Long,
    var endMs: Long,
    val text: String,
    var translation: String? = null
)

data class DesktopLyrics(
    val lines: List<DesktopLyricLine>,
    val sourceName: String,
    val isSynced: Boolean,
    val isInstrumental: Boolean = false
)

object DesktopLyricsRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private fun sanitize(raw: String): String {
        return raw
            .replace(Regex("(?i)\\s*[-\\(\\[]?\\s*\\d{4}\\s*(digital|stereo|mono)?\\s*remaster(ed)?\\s*[\\]\\)]?"), "")
            .replace(Regex("(?i)\\s*[-\\(\\[]?\\s*remaster(ed)?\\s*[\\]\\)]?"), "")
            .replace(Regex("(?i)\\s*[-\\(\\[]?\\s*(deluxe|edition|version|bonus track|official|audio|video|lyric video|hd|4k)\\s*[\\]\\)]?"), "")
            .replace(Regex("(?i)\\s*[-\\(\\[]?\\s*(feat|ft)\\..*[\\]\\)]?"), "")
            .replace(Regex("[\\(\\)\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun fetchLyrics(artist: String?, title: String?, durationMs: Long): DesktopLyrics? = withContext(Dispatchers.IO) {
        val rawTitle = title?.trim() ?: ""
        val rawArtist = artist?.trim() ?: ""
        val cleanTitle = sanitize(rawTitle)
        val cleanArtist = sanitize(rawArtist)

        if (cleanTitle.isEmpty() && cleanArtist.isEmpty()) return@withContext null

        val titles = listOfNotNull(cleanTitle, rawTitle).distinct()
        val artists = listOfNotNull(cleanArtist, rawArtist).distinct()

        var result: DesktopLyrics? = null

        // 1. LRCLIB
        for (t in titles) {
            for (a in artists) {
                result = fetchFromLrclib(a, t, durationMs)
                if (result != null) break
            }
            if (result != null) break
        }

        // 2. NetEase
        if (result == null) {
            for (t in titles) {
                for (a in artists) {
                    result = fetchFromNetease(a, t)
                    if (result != null) break
                }
                if (result != null) break
            }
        }

        // 3. Kugou
        if (result == null) {
            for (t in titles) {
                for (a in artists) {
                    result = fetchFromKugou(a, t, durationMs)
                    if (result != null) break
                }
                if (result != null) break
            }
        }

        if (result != null && result.isSynced && result.lines.isNotEmpty()) {
            translateToRussian(result.lines)
        }

        return@withContext result
    }

    private fun parseLrc(rawText: String): List<DesktopLyricLine>? {
        if (rawText.isBlank()) return null
        val timeRegex = Regex("\\[(\\d+):(\\d{2})([.:]\\d+)?\\]")
        val lines = rawText.lines()
        val parsedLines = mutableListOf<DesktopLyricLine>()

        for (line in lines) {
            val matches = timeRegex.findAll(line).toList()
            if (matches.isEmpty()) continue

            val text = line.replace(timeRegex, "").trim()
            if (text.isEmpty()) continue

            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val secStr = match.groupValues[2] + match.groupValues[3].replace(':', '.')
                val sec = (secStr.toDoubleOrNull() ?: 0.0) * 1000
                val totalMs = (min * 60 * 1000) + sec.toLong()
                parsedLines.add(DesktopLyricLine(startMs = totalMs, endMs = 0L, text = text))
            }
        }

        if (parsedLines.isEmpty()) {
            // Unsynced fallback
            return lines.filter { it.isNotBlank() && !it.startsWith("[") }.map {
                DesktopLyricLine(startMs = 0L, endMs = 0L, text = it.trim())
            }
        }

        parsedLines.sortBy { it.startMs }
        for (i in 0 until parsedLines.size) {
            if (i < parsedLines.size - 1) {
                parsedLines[i].endMs = parsedLines[i + 1].startMs
            } else {
                parsedLines[i].endMs = parsedLines[i].startMs + 5000L
            }
        }
        return parsedLines
    }

    private fun fetchFromLrclib(artist: String, title: String, durationMs: Long): DesktopLyrics? {
        try {
            val durationSec = if (durationMs > 0) durationMs / 1000 else 0
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")

            var url = "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"
            if (durationSec > 0) url += "&duration=$durationSec"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SalvationDesktop/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return null
                val json = JSONObject(jsonStr)

                if (json.optBoolean("instrumental", false)) {
                    return DesktopLyrics(emptyList(), "LRCLIB (Инструментал)", isSynced = false, isInstrumental = true)
                }

                val syncedLrc = json.optString("syncedLyrics", "").trim()
                val plainLrc = json.optString("plainLyrics", "").trim()

                val raw = if (syncedLrc.isNotBlank() && syncedLrc != "null") syncedLrc else plainLrc
                if (raw.isNotBlank() && raw != "null") {
                    val parsed = parseLrc(raw)
                    if (!parsed.isNullOrEmpty()) {
                        return DesktopLyrics(parsed, "LRCLIB", isSynced = syncedLrc.isNotBlank() && syncedLrc != "null")
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun fetchFromNetease(artist: String, title: String): DesktopLyrics? {
        try {
            val query = "$artist $title".trim()
            val searchUrl = "https://music.163.com/api/search/get/web?s=${URLEncoder.encode(query, "UTF-8")}&type=1&limit=3"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64)")
                .header("Referer", "https://music.163.com/")
                .build()

            val searchResp = client.newCall(searchReq).execute()
            if (!searchResp.isSuccessful) return null

            val searchJson = JSONObject(searchResp.body?.string() ?: "")
            val songs = searchJson.optJSONObject("result")?.optJSONArray("songs") ?: return null
            if (songs.length() == 0) return null

            val songId = songs.getJSONObject(0).optLong("id", 0L)
            if (songId == 0L) return null

            val lyricUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"

            val lyricReq = Request.Builder()
                .url(lyricUrl)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64)")
                .header("Referer", "https://music.163.com/")
                .build()

            val lyricResp = client.newCall(lyricReq).execute()
            if (!lyricResp.isSuccessful) return null

            val lyricJson = JSONObject(lyricResp.body?.string() ?: "")
            val lrcText = lyricJson.optJSONObject("lrc")?.optString("lyric", "") ?: ""

            if (lrcText.isNotBlank() && lrcText != "null") {
                val parsed = parseLrc(lrcText)
                if (!parsed.isNullOrEmpty()) {
                    return DesktopLyrics(parsed, "NetEase Music", isSynced = true)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun fetchFromKugou(artist: String, title: String, durationMs: Long): DesktopLyrics? {
        try {
            val query = "$artist $title".trim()
            val searchUrl = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=${URLEncoder.encode(query, "UTF-8")}&page=1&pagesize=3"

            val searchReq = Request.Builder().url(searchUrl).header("User-Agent", "Android").build()
            val searchResp = client.newCall(searchReq).execute()
            if (!searchResp.isSuccessful) return null

            val searchJson = JSONObject(searchResp.body?.string() ?: "")
            val info = searchJson.optJSONObject("data")?.optJSONArray("info") ?: return null
            if (info.length() == 0) return null

            val hash = info.getJSONObject(0).optString("hash", "")
            if (hash.isEmpty()) return null

            val lrcUrl = "http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=${URLEncoder.encode(query, "UTF-8")}&duration=$durationMs&hash=$hash"

            val lrcReq = Request.Builder().url(lrcUrl).header("User-Agent", "Android").build()
            val lrcResp = client.newCall(lrcReq).execute()
            if (!lrcResp.isSuccessful) return null

            val lrcJson = JSONObject(lrcResp.body?.string() ?: "")
            val candidates = lrcJson.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val id = candidates.getJSONObject(0).optString("id", "")
            val accesskey = candidates.getJSONObject(0).optString("accesskey", "")

            if (id.isNotEmpty() && accesskey.isNotEmpty()) {
                val dlUrl = "http://krcs.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8"
                val dlResp = client.newCall(Request.Builder().url(dlUrl).build()).execute()
                if (dlResp.isSuccessful) {
                    val dlJson = JSONObject(dlResp.body?.string() ?: "")
                    val base64 = dlJson.optString("content", "")
                    if (base64.isNotEmpty()) {
                        val decodedBytes = java.util.Base64.getDecoder().decode(base64)
                        val text = String(decodedBytes, Charsets.UTF_8)
                        val parsed = parseLrc(text)
                        if (!parsed.isNullOrEmpty()) {
                            return DesktopLyrics(parsed, "Kugou Music", isSynced = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun translateToRussian(lines: List<DesktopLyricLine>) {
        try {
            val linesToTranslate = lines.filter { it.text.isNotBlank() && it.translation.isNullOrBlank() }
            if (linesToTranslate.isEmpty()) return

            val batchText = StringBuilder()
            linesToTranslate.forEach { line ->
                batchText.append(line.text.replace("\n", " ").trim()).append("\n")
            }

            val encoded = URLEncoder.encode(batchText.toString(), "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ru&dt=t&q=$encoded"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return
                val rootArray = JSONArray(jsonStr)
                val sentences = rootArray.optJSONArray(0) ?: return

                val translatedBuilder = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val sentence = sentences.optJSONArray(i) ?: continue
                    translatedBuilder.append(sentence.optString(0, ""))
                }

                val translatedLines = translatedBuilder.toString().split("\n")
                var idx = 0
                linesToTranslate.forEach { line ->
                    if (idx < translatedLines.size) {
                        val trans = translatedLines[idx].trim()
                        if (trans.isNotEmpty() && trans.lowercase() != line.text.trim().lowercase()) {
                            line.translation = trans
                        }
                        idx++
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore translation errors
        }
    }
}
