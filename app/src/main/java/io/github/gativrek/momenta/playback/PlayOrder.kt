package io.github.gativrek.momenta.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlin.random.Random

/**
 * The queue in the order it will play. With shuffle off that's item order (0, 1, 2, ...);
 * with shuffle on it's the player's shuffle order. Queue edits ("Play next", drag to
 * reorder) are expressed in play order so they mean the same thing in both modes.
 *
 * Lists here hold item indices; a position in the list is a "play position".
 */
object PlayOrder {
    /** Item indices of [timeline] in play order. */
    fun of(
        timeline: Timeline,
        shuffle: Boolean,
    ): List<Int> {
        if (timeline.isEmpty) return emptyList()
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        return order
    }

    /** [order] with the entry at play position [from] moved to play position [to]. */
    fun move(
        order: List<Int>,
        from: Int,
        to: Int,
    ): List<Int> = order.toMutableList().apply { add(to, removeAt(from)) }

    /**
     * A fresh shuffle of [count] items that starts with [first], so turning shuffle on (or
     * starting a list with shuffle on) still plays everything after the current track.
     */
    fun shuffledStartingWith(
        first: Int,
        count: Int,
        random: Random = Random,
    ): List<Int> {
        if (count <= 0) return emptyList()
        val head = first.coerceIn(0, count - 1)
        return listOf(head) + (0 until count).filter { it != head }.shuffled(random)
    }

    /**
     * The play order after [count] new items were inserted at item index [at]. Existing
     * indices at or after [at] shift up; the new items play, in their given order, right
     * after play position [afterPosition] (-1 puts them first).
     */
    fun insert(
        order: List<Int>,
        at: Int,
        count: Int,
        afterPosition: Int,
    ): List<Int> {
        val shifted = order.map { if (it >= at) it + count else it }
        val split = (afterPosition + 1).coerceIn(0, shifted.size)
        return shifted.subList(0, split) + (at until at + count) + shifted.subList(split, shifted.size)
    }
}
