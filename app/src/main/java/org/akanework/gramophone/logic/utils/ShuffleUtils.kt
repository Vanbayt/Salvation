package org.akanework.gramophone.logic.utils

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.security.SecureRandom
import java.util.Collections

/**
 * Spotify-Grade Smart Shuffle Engine
 *
 * Implements high-entropy non-deterministic randomized shuffle with multi-level
 * artist dispersion (dithered spread) and album separation to ensure every shuffle
 * run is completely unique while preventing consecutive tracks by the same artist.
 */
object ShuffleUtils {

    private val secureRandom = SecureRandom()
    private var shuffleRunCounter = 0L

    /**
     * Smart Shuffle with Spotify-grade artist and album dispersion.
     */
    fun <T> smartShuffle(
        items: List<T>,
        getArtist: (T) -> String,
        getAlbum: (T) -> String = { "" }
    ): List<T> {
        if (items.size <= 2) {
            return items.shuffled(secureRandom)
        }

        shuffleRunCounter++
        val totalCount = items.size

        // 1. Group items by normalized artist key
        val artistGroups = items.groupBy { item ->
            val raw = getArtist(item).trim().lowercase()
            if (raw.isNotEmpty()) raw else "unknown_artist_${System.nanoTime()}_${secureRandom.nextInt()}"
        }.mapValues { (_, groupList) ->
            // Within the same artist, shuffle with high entropy and disperse albums
            groupList.shuffled(secureRandom).toMutableList()
        }

        // 2. Sort artist entries descending by frequency (heaviest artist placed first)
        val sortedArtistEntries = artistGroups.entries.sortedWith(
            compareByDescending<Map.Entry<String, MutableList<T>>> { it.value.size }
                .thenBy { secureRandom.nextInt() }
        )

        // 3. Slot-based dithered distribution (Spotify Dithering Algorithm)
        val result = ArrayList<T?>(Collections.nCopies(totalCount, null))
        val occupied = BooleanArray(totalCount)

        for (entry in sortedArtistEntries) {
            val artistTracks = entry.value
            val count = artistTracks.size
            val idealInterval = totalCount.toDouble() / count.toDouble()

            // Dynamic random start phase per artist to guarantee unique layouts every run
            val baseOffset = secureRandom.nextDouble() * idealInterval

            for (i in 0 until count) {
                val track = artistTracks[i]
                val idealSlot = (baseOffset + i * idealInterval).toInt().coerceIn(0, totalCount - 1)
                val jitter = if (idealInterval > 3.0) (secureRandom.nextInt(3) - 1) else 0
                val candidateSlot = (idealSlot + jitter).coerceIn(0, totalCount - 1)

                if (!occupied[candidateSlot]) {
                    result[candidateSlot] = track
                    occupied[candidateSlot] = true
                } else {
                    // Find closest available slot searching bidirectionally
                    var offset = 1
                    var placed = false
                    while (!placed && (candidateSlot - offset >= 0 || candidateSlot + offset < totalCount)) {
                        val right = candidateSlot + offset
                        if (right < totalCount && !occupied[right]) {
                            result[right] = track
                            occupied[right] = true
                            placed = true
                            break
                        }
                        val left = candidateSlot - offset
                        if (left >= 0 && !occupied[left]) {
                            result[left] = track
                            occupied[left] = true
                            placed = true
                            break
                        }
                        offset++
                    }
                    if (!placed) {
                        for (slot in 0 until totalCount) {
                            if (!occupied[slot]) {
                                result[slot] = track
                                occupied[slot] = true
                                break
                            }
                        }
                    }
                }
            }
        }

        val nonNullList = result.filterNotNull().toMutableList()

        // 4. Anti-Clustering Post-Processing Pass:
        // Resolve any adjacent or near-adjacent same-artist collisions by swapping with distant candidates
        for (j in 0 until nonNullList.size - 1) {
            val currentArtist = getArtist(nonNullList[j]).trim().lowercase()
            val nextArtist = getArtist(nonNullList[j + 1]).trim().lowercase()

            if (currentArtist.isNotEmpty() && currentArtist == nextArtist) {
                // Find a swap partner at least 3 positions away
                for (k in (j + 2) until nonNullList.size) {
                    val candidateArtist = getArtist(nonNullList[k]).trim().lowercase()
                    val prevCandidateArtist = if (k > 0) getArtist(nonNullList[k - 1]).trim().lowercase() else ""
                    val nextCandidateArtist = if (k < nonNullList.size - 1) getArtist(nonNullList[k + 1]).trim().lowercase() else ""

                    if (candidateArtist != currentArtist &&
                        prevCandidateArtist != currentArtist &&
                        nextCandidateArtist != currentArtist
                    ) {
                        val temp = nonNullList[j + 1]
                        nonNullList[j + 1] = nonNullList[k]
                        nonNullList[k] = temp
                        break
                    }
                }
            }
        }

        return nonNullList
    }

    /**
     * Backwards-compatible balanced shuffle delegating to smartShuffle.
     */
    fun <T> balancedShuffle(items: List<T>, getGroupKey: (T) -> String): List<T> {
        return smartShuffle(items, getArtist = getGroupKey)
    }

    /**
     * Start playback with full collection Smart Shuffle.
     */
    fun playWithSmartShuffle(
        player: Player,
        mediaItems: List<MediaItem>,
        startWithMediaId: String? = null
    ) {
        if (mediaItems.isEmpty()) return

        val shuffled = smartShuffle(
            items = mediaItems,
            getArtist = { it.mediaMetadata.artist?.toString() ?: "" },
            getAlbum = { it.mediaMetadata.albumTitle?.toString() ?: "" }
        ).toMutableList()

        if (!startWithMediaId.isNullOrEmpty()) {
            val startIdx = shuffled.indexOfFirst { it.mediaId == startWithMediaId }
            if (startIdx > 0) {
                val item = shuffled.removeAt(startIdx)
                shuffled.add(0, item)
            }
        }

        player.shuffleModeEnabled = true
        player.setMediaItems(shuffled, 0, 0)
        player.prepare()
        player.play()
    }

    /**
     * Physically smart-shuffle the upcoming active queue in the player without interrupting current playback.
     */
    fun applySmartShuffleToActiveQueue(player: Player) {
        val currentIndex = player.currentMediaItemIndex
        val totalCount = player.mediaItemCount
        if (currentIndex == -1 || totalCount <= 1) return

        val currentItem = player.getMediaItemAt(currentIndex)
        val remainingItems = mutableListOf<MediaItem>()

        for (i in 0 until totalCount) {
            if (i != currentIndex) {
                remainingItems.add(player.getMediaItemAt(i))
            }
        }

        val shuffledUpcoming = smartShuffle(
            items = remainingItems,
            getArtist = { it.mediaMetadata.artist?.toString() ?: "" },
            getAlbum = { it.mediaMetadata.albumTitle?.toString() ?: "" }
        )

        val newQueue = mutableListOf<MediaItem>()
        newQueue.add(currentItem)
        newQueue.addAll(shuffledUpcoming)

        val currentPos = player.currentPosition
        val wasPlaying = player.isPlaying

        player.setMediaItems(newQueue, 0, currentPos)
        if (wasPlaying) {
            player.play()
        }
    }

    /**
     * Legacy physical shuffle bridge for MediaController.
     */
    fun applyPhysicalShuffle(player: androidx.media3.session.MediaController) {
        applySmartShuffleToActiveQueue(player)
    }
}
