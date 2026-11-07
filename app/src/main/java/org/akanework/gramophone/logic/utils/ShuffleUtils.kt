package org.akanework.gramophone.logic.utils

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.akanework.gramophone.logic.stats.AffinityProfile
import org.akanework.gramophone.logic.stats.PlaybackStatsDatabase
import org.akanework.gramophone.logic.stats.StatsTracker
import java.security.SecureRandom
import java.util.Collections

/**
 * Spotify-Grade Smart Shuffle Engine with User Listening Affinity & History Cache.
 *
 * Implements:
 * 1. Listening Affinity awareness (priors tracks and artists recently played on repeat)
 * 2. Gradient interleaving with discovery catalogue (smooth variety without repetition)
 * 3. Multi-run history caching to eliminate strict position and pair matches across shuffles
 * 4. Anti-clustering artist and album dispersion
 * 5. Full restoration of the original queue when shuffle mode is toggled off
 */
object ShuffleUtils {

    private val secureRandom = SecureRandom()
    private const val MAX_HISTORY_PER_COLLECTION = 5
    private const val AFFINITY_CACHE_TTL_MS = 60 * 1000L

    // In-memory cache of past shuffle orders (CollectionKey -> list of track ID sequences)
    private val shuffleHistoryCache = object : LinkedHashMap<String, ArrayDeque<List<String>>>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<List<String>>>?): Boolean {
            return size > 30
        }
    }

    // Cached original (unshuffled) queue for seamless restoration
    @Volatile
    private var originalQueue: List<MediaItem>? = null

    // In-memory cached AffinityProfile (hot tracks and artists on repeat)
    @Volatile
    private var cachedAffinityProfile: Pair<Long, AffinityProfile>? = null

    /**
     * Retrieves recent user affinity profile (hot tracks & artists) from local stats DB.
     * Caches in RAM for 1 minute for zero-latency queries while staying responsive to updates.
     */
    fun getAffinityProfile(context: Context?): AffinityProfile {
        if (context == null) return AffinityProfile()
        val now = System.currentTimeMillis()
        cachedAffinityProfile?.let { (timestamp, profile) ->
            if (now - timestamp < AFFINITY_CACHE_TTL_MS) {
                return profile
            }
        }
        return try {
            val userId = StatsTracker.getUserId(context)
            val profile = PlaybackStatsDatabase.getInstance(context).getRecentAffinityProfile(userId, days = 14)
            cachedAffinityProfile = now to profile
            android.util.Log.i("ShuffleUtils", "getAffinityProfile: loaded ${profile.megaRepeatTrackIds.size} mega-repeats, ${profile.hotTrackIds.size} hot tracks, ${profile.hotArtists.size} hot artists, ${profile.hotAlbums.size} hot albums")
            profile
        } catch (e: Exception) {
            android.util.Log.e("ShuffleUtils", "getAffinityProfile error: ${e.message}", e)
            AffinityProfile()
        }
    }

    /**
     * Checks whether Smart Shuffle has active listening affinity data (mega-repeats, hot tracks/artists).
     */
    fun isSmartShuffleActive(context: Context?): Boolean {
        return getAffinityProfile(context).isSmart
    }

    /**
     * Smart Shuffle with Spotify-grade artist/album dispersion, listening affinity, and history caching.
     */
    fun <T> smartShuffle(
        items: List<T>,
        getArtist: (T) -> String,
        getAlbum: (T) -> String = { "" },
        getId: (T) -> String = { it.hashCode().toString() },
        getTitle: (T) -> String = { "" },
        collectionKey: String? = null,
        affinityProfile: AffinityProfile? = null
    ): List<T> {
        if (items.size <= 2) {
            return if (items.size == 2 && secureRandom.nextBoolean()) items.reversed() else items
        }

        val total = items.size

        // 1. Compute or resolve collection key for history tracking
        val effectiveKey = collectionKey?.ifBlank { null } ?: run {
            if (total > 20) {
                val p0 = getId(items[0])
                val p1 = getId(items[total / 4])
                val p2 = getId(items[total / 2])
                val p3 = getId(items[3 * total / 4])
                val p4 = getId(items[total - 1])
                "${total}_${p0}_${p1}_${p2}_${p3}_${p4}"
            } else {
                "${total}_" + items.joinToString("_") { getId(it) }
            }
        }

        // 2. Fetch history constraints for this collection
        val history = synchronized(shuffleHistoryCache) {
            shuffleHistoryCache.getOrPut(effectiveKey) { ArrayDeque(MAX_HISTORY_PER_COLLECTION) }
        }

        val pastFirstTracks = synchronized(shuffleHistoryCache) {
            history.takeLast(3).mapNotNull { it.firstOrNull() }.toSet()
        }
        val pastBigrams = mutableSetOf<Pair<String, String>>()
        val pastTopQuartile = mutableSetOf<String>()
        val lastOrder = synchronized(shuffleHistoryCache) { history.lastOrNull() }

        if (lastOrder != null) {
            for (i in 0 until lastOrder.size) {
                val tid = lastOrder[i]
                if (i < lastOrder.size - 1) {
                    pastBigrams.add(tid to lastOrder[i + 1])
                }
                if (i < total / 5) {
                    pastTopQuartile.add(tid)
                }
            }
        }

        // 3. Affinity Partitioning: Separate into Mega (real repeats), Hot (recent favorites), Similar (hot artists & albums), and Discovery (rest)
        val hasAffinity = affinityProfile != null && (
            affinityProfile.megaRepeatTrackIds.isNotEmpty() ||
            affinityProfile.hotTrackIds.isNotEmpty() ||
            affinityProfile.hotArtists.isNotEmpty() ||
            affinityProfile.hotAlbums.isNotEmpty()
        )
        var poolMegaCount = 0
        var poolHotCount = 0
        var poolSimilarCount = 0
        var poolDiscoveryCount = 0

        val res = if (hasAffinity) {
            val poolMega = mutableListOf<T>()
            val poolHot = mutableListOf<T>()
            val poolSimilar = mutableListOf<T>()
            val poolDiscovery = mutableListOf<T>()

            val megaTracks = affinityProfile.megaRepeatTrackIds
            val hotTracks = affinityProfile.hotTrackIds
            val hotArtists = affinityProfile.hotArtists
            val hotAlbums = affinityProfile.hotAlbums
            val penalizedTracks = affinityProfile.penalizedTrackIds

            for (item in items) {
                val tid = getId(item)
                val artist = getArtist(item).trim().lowercase()
                val album = getAlbum(item).trim().lowercase()
                when {
                    penalizedTracks.contains(tid) -> poolDiscovery.add(item)
                    megaTracks.contains(tid) -> poolMega.add(item)
                    hotTracks.contains(tid) -> poolHot.add(item)
                    (artist.isNotEmpty() && hotArtists.contains(artist)) || (album.isNotEmpty() && hotAlbums.contains(album)) -> poolSimilar.add(item)
                    else -> poolDiscovery.add(item)
                }
            }

            poolMegaCount = poolMega.size
            poolHotCount = poolHot.size
            poolSimilarCount = poolSimilar.size
            poolDiscoveryCount = poolDiscovery.size

            // Shuffle each sub-pool independently
            fisherYatesShuffle(poolMega)
            fisherYatesShuffle(poolHot)
            fisherYatesShuffle(poolSimilar)
            fisherYatesShuffle(poolDiscovery)

            val interleaved = ArrayList<T>(total)
            var mIdx = 0
            var hIdx = 0
            var sIdx = 0
            var dIdx = 0

            // Adaptive interleaving chance based on total collection size
            val megaChance = when {
                total <= 20 -> 0.30
                total <= 50 -> 0.38
                else -> 0.45
            }

            for (step in 0 until total) {
                if (mIdx < poolMega.size) {
                    // Mega phase: prominently weave the true top-repeats into the first items
                    val roll = secureRandom.nextDouble()
                    if (roll < megaChance && mIdx < poolMega.size) {
                        interleaved.add(poolMega[mIdx++])
                    } else if (roll < 0.70 && hIdx < poolHot.size) {
                        interleaved.add(poolHot[hIdx++])
                    } else if (roll < 0.85 && sIdx < poolSimilar.size) {
                        interleaved.add(poolSimilar[sIdx++])
                    } else if (dIdx < poolDiscovery.size) {
                        interleaved.add(poolDiscovery[dIdx++])
                    } else if (sIdx < poolSimilar.size) {
                        interleaved.add(poolSimilar[sIdx++])
                    } else if (hIdx < poolHot.size) {
                        interleaved.add(poolHot[hIdx++])
                    } else {
                        interleaved.add(poolMega[mIdx++])
                    }
                } else if (hIdx < poolHot.size || step < 50) {
                    // Hot/Familiar phase: 40% hot, 35% similar, 25% discovery
                    val roll = secureRandom.nextDouble()
                    if (roll < 0.40 && hIdx < poolHot.size) {
                        interleaved.add(poolHot[hIdx++])
                    } else if (roll < 0.75 && sIdx < poolSimilar.size) {
                        interleaved.add(poolSimilar[sIdx++])
                    } else if (dIdx < poolDiscovery.size) {
                        interleaved.add(poolDiscovery[dIdx++])
                    } else if (sIdx < poolSimilar.size) {
                        interleaved.add(poolSimilar[sIdx++])
                    } else if (hIdx < poolHot.size) {
                        interleaved.add(poolHot[hIdx++])
                    }
                } else if (step < 120) {
                    // Familiar phase: smooth blend of similar artists and discovery
                    val roll = secureRandom.nextDouble()
                    if (roll < 0.40 && sIdx < poolSimilar.size) {
                        interleaved.add(poolSimilar[sIdx++])
                    } else if (dIdx < poolDiscovery.size) {
                        interleaved.add(poolDiscovery[dIdx++])
                    } else if (sIdx < poolSimilar.size) {
                        interleaved.add(poolSimilar[sIdx++])
                    }
                } else {
                    // Catalog discovery phase: 15% similar, 85% discovery
                    val roll = secureRandom.nextDouble()
                    if (roll < 0.15 && sIdx < poolSimilar.size) {
                        interleaved.add(poolSimilar[sIdx++])
                    } else if (dIdx < poolDiscovery.size) {
                        interleaved.add(poolDiscovery[dIdx++])
                    } else if (sIdx < poolSimilar.size) {
                        interleaved.add(poolSimilar[sIdx++])
                    }
                }
            }

            // Fill in any remainder if one pool had extra
            while (mIdx < poolMega.size) interleaved.add(poolMega[mIdx++])
            while (hIdx < poolHot.size) interleaved.add(poolHot[hIdx++])
            while (sIdx < poolSimilar.size) interleaved.add(poolSimilar[sIdx++])
            while (dIdx < poolDiscovery.size) interleaved.add(poolDiscovery[dIdx++])

            interleaved
        } else {
            val copy = ArrayList(items)
            fisherYatesShuffle(copy)
            copy
        }

        // 4. Cache Dissimilarity & Anti-Repetition Pass with Adaptive Priority Zone Preservation
        val priorityBoundary = when {
            res.size <= 8 -> res.size
            res.size <= 20 -> (res.size / 2).coerceAtLeast(3)
            res.size <= 60 -> minOf(res.size / 2, 25)
            res.size <= 150 -> minOf(res.size / 3, 40)
            else -> minOf(res.size / 4, 50).coerceAtLeast(30)
        }
        val hotIds = (affinityProfile?.megaRepeatTrackIds ?: emptySet()) + (affinityProfile?.hotTrackIds ?: emptySet())
        val penalizedTracks = affinityProfile?.penalizedTrackIds ?: emptySet()

        // 4-pre. Displace any penalized (frequently skipped) tracks from the priority head into the discovery tail
        if (penalizedTracks.isNotEmpty() && res.size > priorityBoundary + 5) {
            for (i in 0 until priorityBoundary) {
                val tid = getId(res[i])
                if (penalizedTracks.contains(tid)) {
                    for (k in priorityBoundary until res.size) {
                        val candId = getId(res[k])
                        if (!penalizedTracks.contains(candId) && !hotIds.contains(candId)) {
                            val tmp = res[i]
                            res[i] = res[k]
                            res[k] = tmp
                            break
                        }
                    }
                }
            }
        }

        // 4a. Guarantee new starting track (do not repeat recent first tracks)
        if (res.size > 5 && pastFirstTracks.contains(getId(res[0]))) {
            val curArtist = getArtist(res[0]).trim().lowercase()
            val maxCand = minOf(res.size, priorityBoundary)
            for (idx in 1 until maxCand) {
                val candId = getId(res[idx])
                val candArtist = getArtist(res[idx]).trim().lowercase()
                if (!pastFirstTracks.contains(candId) && !penalizedTracks.contains(candId) && candArtist != curArtist) {
                    val tmp = res[0]
                    res[0] = res[idx]
                    res[idx] = tmp
                    break
                }
            }
        }

        // 4b. Displace discovery tracks that were in the top 20% in the last run (to rotate library discovery)
        // Hot repeat tracks are strictly protected so they never get thrown out of the priority head
        val topCut = res.size / 5
        if (topCut > priorityBoundary && pastTopQuartile.isNotEmpty()) {
            for (i in priorityBoundary until topCut) {
                val tid = getId(res[i])
                if (!hotIds.contains(tid) && pastTopQuartile.contains(tid) && secureRandom.nextDouble() < 0.65) {
                    val farIdx = topCut + secureRandom.nextInt(res.size - topCut)
                    val candFarId = getId(res[farIdx])
                    if (!hotIds.contains(candFarId)) {
                        val tmp = res[i]
                        res[i] = res[farIdx]
                        res[farIdx] = tmp
                    }
                }
            }
        }

        // 4c. Break recent consecutive pairs (bigrams) within each respective zone
        if (pastBigrams.isNotEmpty()) {
            for (i in 0 until res.size - 1) {
                val pair = getId(res[i]) to getId(res[i + 1])
                if (pastBigrams.contains(pair)) {
                    if (i < priorityBoundary - 2) {
                        // In priority head: swap WITHIN priority head to keep repeats near top!
                        val targetIdx = (i + 2 + secureRandom.nextInt(priorityBoundary - i - 2)) % priorityBoundary
                        if (targetIdx != i && targetIdx != i + 1) {
                            val tmp = res[i + 1]
                            res[i + 1] = res[targetIdx]
                            res[targetIdx] = tmp
                        }
                    } else if (i >= priorityBoundary) {
                        // In discovery tail: swap within tail
                        val tailSize = res.size - priorityBoundary
                        if (tailSize > 3) {
                            val offset = secureRandom.nextInt(tailSize / 2) + 2
                            val targetIdx = priorityBoundary + ((i - priorityBoundary + offset) % tailSize)
                            if (targetIdx != i && targetIdx != i + 1) {
                                val tmp = res[i + 1]
                                res[i + 1] = res[targetIdx]
                                res[targetIdx] = tmp
                            }
                        }
                    }
                }
            }
        }

        // 4d. Break exact position matches with zone preservation
        if (lastOrder != null && lastOrder.size == res.size) {
            for (i in 0 until res.size) {
                if (getId(res[i]) == lastOrder[i]) {
                    if (i < priorityBoundary) {
                        val offset = secureRandom.nextInt(priorityBoundary / 2).coerceAtLeast(1) + 1
                        val targetIdx = (i + offset) % priorityBoundary
                        val tmp = res[i]
                        res[i] = res[targetIdx]
                        res[targetIdx] = tmp
                    } else {
                        val tailSize = res.size - priorityBoundary
                        if (tailSize > 3) {
                            val offset = secureRandom.nextInt(tailSize / 3).coerceAtLeast(1) + 1
                            val targetIdx = priorityBoundary + ((i - priorityBoundary + offset) % tailSize)
                            val tmp = res[i]
                            res[i] = res[targetIdx]
                            res[targetIdx] = tmp
                        }
                    }
                }
            }
        }

        // 5. Spotify-Grade Artist & Album Anti-Clustering Pass with Zone Preservation
        for (i in 0 until res.size - 1) {
            val a1 = getArtist(res[i]).trim().lowercase()
            val a2 = getArtist(res[i + 1]).trim().lowercase()
            if (a1.isNotEmpty() && a1 == a2) {
                var swapped = false
                val inPriority = (i < priorityBoundary)
                val searchRange = if (inPriority) 2 until priorityBoundary else 2 until minOf(res.size - i, 60)
                for (step in searchRange) {
                    val target = if (inPriority) (i + step) % priorityBoundary else (i + step) % res.size
                    if (target == i || target == i + 1) continue
                    val prevTarget = (target - 1 + res.size) % res.size
                    val nextTarget = (target + 1) % res.size

                    val targetA = getArtist(res[target]).trim().lowercase()
                    val prevA = getArtist(res[prevTarget]).trim().lowercase()
                    val nextA = getArtist(res[nextTarget]).trim().lowercase()

                    if (targetA != a1 && prevA != a2 && nextA != a2) {
                        val tmp = res[i + 1]
                        res[i + 1] = res[target]
                        res[target] = tmp
                        swapped = true
                        break
                    }
                }
                if (!swapped) {
                    val startK = i + 2
                    val endK = if (inPriority) priorityBoundary else res.size
                    for (k in startK until endK) {
                        if (getArtist(res[k]).trim().lowercase() != a1) {
                            val tmp = res[i + 1]
                            res[i + 1] = res[k]
                            res[k] = tmp
                            break
                        }
                    }
                }
            }
        }

        // 5b. Small Queue Pacing: avoid clumping mega-repeats at the very start in short collections
        if (res.size in 4..25 && poolMegaCount >= 2) {
            val megaSet = affinityProfile?.megaRepeatTrackIds ?: emptySet()
            if (megaSet.contains(getId(res[0])) && megaSet.contains(getId(res[1]))) {
                for (k in 2 until priorityBoundary) {
                    if (!megaSet.contains(getId(res[k]))) {
                        val tmp = res[1]
                        res[1] = res[k]
                        res[k] = tmp
                        break
                    }
                }
            }
        }

        // 6. Record newly generated order in cache
        val idOrder = res.map(getId)
        synchronized(shuffleHistoryCache) {
            if (history.size >= MAX_HISTORY_PER_COLLECTION) {
                history.removeFirst()
            }
            history.add(idOrder)
        }

        android.util.Log.i(
            "ShuffleUtils",
            "smartShuffle done: total=$total, megaPool=$poolMegaCount, hotPool=$poolHotCount, similarPool=$poolSimilarCount, discoveryPool=$poolDiscoveryCount. Head: " +
                res.take(7).joinToString {
                    val a = getArtist(it)
                    val t = getTitle(it)
                    if (t.isNotEmpty()) "$a - $t" else "$a (${getId(it)})"
                }
        )

        return res
    }

    private fun <T> fisherYatesShuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
    }

    /**
     * Smart Shuffle specifically for MediaItems with context-aware affinity.
     */
    fun smartShuffleMediaItems(
        items: List<MediaItem>,
        context: Context? = null,
        collectionKey: String? = null
    ): List<MediaItem> = smartShuffle(
        items = items,
        getArtist = { it.mediaMetadata.artist?.toString() ?: "" },
        getAlbum = { it.mediaMetadata.albumTitle?.toString() ?: "" },
        getId = { it.mediaId.ifEmpty { it.requestMetadata.mediaUri?.toString() ?: it.hashCode().toString() } },
        getTitle = { it.mediaMetadata.title?.toString() ?: "" },
        collectionKey = collectionKey,
        affinityProfile = getAffinityProfile(context)
    )

    /**
     * Start playback with full collection Smart Shuffle.
     */
    fun playWithSmartShuffle(
        player: Player,
        mediaItems: List<MediaItem>,
        startWithMediaId: String? = null,
        context: Context? = null,
        collectionKey: String? = null
    ) {
        if (mediaItems.isEmpty()) return

        // Remember original un-shuffled queue for future toggle off
        originalQueue = mediaItems.toList()

        val shuffled = smartShuffleMediaItems(
            items = mediaItems,
            context = context,
            collectionKey = collectionKey
        ).toMutableList()

        if (!startWithMediaId.isNullOrEmpty()) {
            val startIdx = shuffled.indexOfFirst { it.mediaId == startWithMediaId }
            if (startIdx > 0) {
                val item = shuffled.removeAt(startIdx)
                shuffled.add(0, item)

                // Resolve possible artist collision with item at index 1
                if (shuffled.size > 2) {
                    val a0 = shuffled[0].mediaMetadata.artist?.toString()?.trim()?.lowercase() ?: ""
                    val a1 = shuffled[1].mediaMetadata.artist?.toString()?.trim()?.lowercase() ?: ""
                    if (a0.isNotEmpty() && a0 == a1) {
                        for (k in 2 until minOf(shuffled.size, 20)) {
                            val ak = shuffled[k].mediaMetadata.artist?.toString()?.trim()?.lowercase() ?: ""
                            if (ak != a0) {
                                val tmp = shuffled[1]
                                shuffled[1] = shuffled[k]
                                shuffled[k] = tmp
                                break
                            }
                        }
                    }
                }
            }
        }

        player.shuffleModeEnabled = true
        player.setMediaItems(shuffled, 0, 0L)
        player.prepare()
        player.play()
        android.util.Log.i("ShuffleUtils", "playWithSmartShuffle: started with ${shuffled.size} items, first is ${shuffled.firstOrNull()?.mediaMetadata?.title}")
    }

    /**
     * Physically smart-shuffle the upcoming active queue without interrupting current playback.
     */
    fun applySmartShuffleToActiveQueue(
        player: Player,
        context: Context? = null
    ) {
        val currentIndex = player.currentMediaItemIndex
        val totalCount = player.mediaItemCount
        if (currentIndex == -1 || totalCount <= 1) return

        val currentItem = player.getMediaItemAt(currentIndex)
        val allItems = ArrayList<MediaItem>(totalCount)
        val remainingItems = ArrayList<MediaItem>(totalCount - 1)

        for (i in 0 until totalCount) {
            val item = player.getMediaItemAt(i)
            allItems.add(item)
            if (i != currentIndex) {
                remainingItems.add(item)
            }
        }

        // Cache original order if not captured yet
        if (originalQueue == null || originalQueue!!.size != totalCount) {
            originalQueue = allItems
        }

        android.util.Log.i("ShuffleUtils", "applySmartShuffleToActiveQueue: shuffling ${remainingItems.size} upcoming items (current: ${currentItem.mediaMetadata.title})")

        val shuffledUpcoming = smartShuffleMediaItems(
            items = remainingItems,
            context = context
        ).toMutableList()

        // Disperse artist collision between currently playing item and first upcoming item
        if (shuffledUpcoming.isNotEmpty()) {
            val curArtist = currentItem.mediaMetadata.artist?.toString()?.trim()?.lowercase() ?: ""
            val nextArtist = shuffledUpcoming[0].mediaMetadata.artist?.toString()?.trim()?.lowercase() ?: ""
            if (curArtist.isNotEmpty() && curArtist == nextArtist && shuffledUpcoming.size > 1) {
                for (k in 1 until minOf(shuffledUpcoming.size, 20)) {
                    val candArtist = shuffledUpcoming[k].mediaMetadata.artist?.toString()?.trim()?.lowercase() ?: ""
                    if (candArtist != curArtist) {
                        val tmp = shuffledUpcoming[0]
                        shuffledUpcoming[0] = shuffledUpcoming[k]
                        shuffledUpcoming[k] = tmp
                        break
                    }
                }
            }
        }

        val newQueue = ArrayList<MediaItem>(totalCount)
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
     * Restores the original un-shuffled linear queue, smoothly keeping the currently playing track.
     */
    fun restoreOriginalQueue(player: Player) {
        val original = originalQueue
        if (original.isNullOrEmpty()) {
            player.shuffleModeEnabled = false
            return
        }

        val currentId = player.currentMediaItem?.mediaId
        val currentPos = player.currentPosition
        val wasPlaying = player.isPlaying

        val targetIndex = if (!currentId.isNullOrEmpty()) {
            original.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
        } else {
            player.currentMediaItemIndex.coerceIn(0, original.size - 1)
        }

        player.shuffleModeEnabled = false
        player.setMediaItems(original, targetIndex, currentPos)
        if (wasPlaying) {
            player.play()
        }
    }

    /**
     * Unified shuffle toggle handler for all player screens.
     * When toggling ON: smart-shuffles upcoming queue using affinity & cache.
     * When toggling OFF: seamlessly restores the original linear queue.
     */
    fun toggleShuffle(
        player: Player,
        context: Context? = null,
        forceState: Boolean? = null
    ): Boolean {
        val target = forceState ?: !player.shuffleModeEnabled
        if (target) {
            player.shuffleModeEnabled = true
            applySmartShuffleToActiveQueue(player, context)
        } else {
            restoreOriginalQueue(player)
        }
        return target
    }

    /**
     * Re-shuffles the active queue on demand (e.g. repeated shuffle click or long press).
     * Guarantees a fresh permutation with zero strict matches with the previous run.
     */
    fun reshuffleActiveQueue(
        player: Player,
        context: Context? = null
    ) {
        player.shuffleModeEnabled = true
        applySmartShuffleToActiveQueue(player, context)
    }

    /**
     * Saves original queue externally (e.g. when a new linear playlist/album starts).
     */
    fun setOriginalQueue(mediaItems: List<MediaItem>) {
        originalQueue = mediaItems.toList()
    }

    /**
     * Backwards-compatible balanced shuffle delegating to smartShuffle.
     */
    fun <T> balancedShuffle(items: List<T>, getGroupKey: (T) -> String): List<T> {
        return smartShuffle(items, getArtist = getGroupKey)
    }

    /**
     * Legacy physical shuffle bridge for MediaController.
     */
    fun applyPhysicalShuffle(player: androidx.media3.session.MediaController, context: Context? = null) {
        applySmartShuffleToActiveQueue(player, context)
    }
}
