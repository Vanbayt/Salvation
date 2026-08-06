package org.akanework.gramophone.logic.utils

import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import java.security.SecureRandom

object ShuffleUtils {
    private val random = SecureRandom()

    fun <T> balancedShuffle(items: List<T>, getGroupKey: (T) -> String): List<T> {
        if (items.size <= 2) return items.shuffled(random)

        val pool = items.toMutableList()
        val result = ArrayList<T>(items.size)

        var lastGroup = ""
        var secondLastGroup = ""

        while (pool.isNotEmpty()) {
            val candidates = pool.filter { item ->
                val group = getGroupKey(item)
                group.isEmpty() || (group != lastGroup && group != secondLastGroup)
            }

            val chosen: T = if (candidates.isNotEmpty()) {
                candidates[random.nextInt(candidates.size)]
            } else {
                val fallbackCandidates = pool.filter { item ->
                    val group = getGroupKey(item)
                    group.isEmpty() || group != lastGroup
                }
                if (fallbackCandidates.isNotEmpty()) {
                    fallbackCandidates[random.nextInt(fallbackCandidates.size)]
                } else {
                    pool[random.nextInt(pool.size)]
                }
            }

            pool.remove(chosen)
            result.add(chosen)

            secondLastGroup = lastGroup
            lastGroup = getGroupKey(chosen)
        }

        return result
    }

    fun applyPhysicalShuffle(player: MediaController) {
        val currentItemIndex = player.currentMediaItemIndex
        val itemCount = player.mediaItemCount
        if (currentItemIndex == -1 || itemCount <= 1) return

        val allItems = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) {
            allItems.add(player.getMediaItemAt(i))
        }

        // Удаляем текущий воспроизводимый трек из перемешиваемого пула
        val currentItem = allItems.removeAt(currentItemIndex)

        // Перемешиваем остальные треки алгоритмом Balanced Shuffle (разделение по артистам)
        val shuffledRest = balancedShuffle(allItems) { item ->
            item.mediaMetadata.artist?.toString()?.lowercase() ?: ""
        }

        // Делим перемешанный список на части "до" и "после" текущего индекса,
        // чтобы сам текущий воспроизводимый элемент (currentItemIndex) остался нетронутым!
        val before = shuffledRest.take(currentItemIndex)
        val after = shuffledRest.drop(currentItemIndex)

        if (before.isNotEmpty() && currentItemIndex > 0) {
            player.replaceMediaItems(0, currentItemIndex, before)
        }
        if (after.isNotEmpty() && currentItemIndex + 1 < itemCount) {
            player.replaceMediaItems(currentItemIndex + 1, itemCount, after)
        }
    }
}
