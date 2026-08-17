package org.akanework.gramophone.logic

import java.util.concurrent.ConcurrentHashMap

// Синглтон, который хранит ID и сигнатуры (title_artist) всех лайкнутых треков в оперативной памяти
object LikeCache {
    val likedTracks = ConcurrentHashMap.newKeySet<String>()
    val likedSignatures = ConcurrentHashMap.newKeySet<String>()

    private fun normalize(str: String): String {
        return str.lowercase().trim()
            .replace(Regex("[\\[\\(].*?[\\]\\)]"), "")
            .replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ]"), "")
    }

    fun makeSignature(title: String?, artist: String?): String? {
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return null
        val normTitle = normalize(title)
        val normArtist = normalize(artist)
        if (normTitle.isEmpty() || normArtist.isEmpty()) return null
        return "${normTitle}_$normArtist"
    }

    fun add(id: String?, sourceId: String? = null, title: String? = null, artist: String? = null) {
        listOfNotNull(id, sourceId).forEach { trackId ->
            if (trackId.isNotEmpty()) {
                likedTracks.add(trackId)
                if (trackId.startsWith("deezer_")) {
                    likedTracks.add(trackId.removePrefix("deezer_"))
                } else if (trackId.all { it.isDigit() }) {
                    likedTracks.add("deezer_$trackId")
                }
            }
        }
        makeSignature(title, artist)?.let { likedSignatures.add(it) }
    }

    fun remove(id: String?, sourceId: String? = null, title: String? = null, artist: String? = null) {
        listOfNotNull(id, sourceId).forEach { trackId ->
            if (trackId.isNotEmpty()) {
                likedTracks.remove(trackId)
                if (trackId.startsWith("deezer_")) {
                    likedTracks.remove(trackId.removePrefix("deezer_"))
                } else if (trackId.all { it.isDigit() }) {
                    likedTracks.remove("deezer_$trackId")
                }
            }
        }
        makeSignature(title, artist)?.let { likedSignatures.remove(it) }
    }

    fun isLiked(id: String?, sourceId: String? = null, title: String? = null, artist: String? = null): Boolean {
        if (!id.isNullOrEmpty() && likedTracks.contains(id)) return true
        if (!sourceId.isNullOrEmpty() && likedTracks.contains(sourceId)) return true
        if (!id.isNullOrEmpty()) {
            if (id.startsWith("deezer_") && likedTracks.contains(id.removePrefix("deezer_"))) return true
            if (likedTracks.contains("deezer_$id")) return true
        }
        if (!sourceId.isNullOrEmpty()) {
            if (sourceId.startsWith("deezer_") && likedTracks.contains(sourceId.removePrefix("deezer_"))) return true
            if (likedTracks.contains("deezer_$sourceId")) return true
        }
        makeSignature(title, artist)?.let { sig ->
            if (likedSignatures.contains(sig)) return true
        }
        return false
    }

    fun clear() {
        likedTracks.clear()
        likedSignatures.clear()
    }
}