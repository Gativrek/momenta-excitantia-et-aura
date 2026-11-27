package io.github.gativrek.momenta.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random

/**
 * The player the MediaSession exposes: ExoPlayer plus the shuffle behaviour we want, for
 * every controller (our UI, the notification, Bluetooth).
 *
 * ExoPlayer's own shuffle order is random over the whole list, so turning shuffle on
 * mid-album (or tapping track 5 with shuffle on) can land the current track near the end of
 * the shuffled order, with only a few tracks left to play. Here, every time shuffle starts
 * or the list is replaced while shuffled, the order is redrawn starting from the current track.
 */
@OptIn(UnstableApi::class)
class QueuePlayer(
    val exoPlayer: ExoPlayer,
) : ForwardingPlayer(exoPlayer) {
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled && !exoPlayer.shuffleModeEnabled) reshuffleFromCurrent()
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        super.setMediaItems(mediaItems)
        afterListReplaced()
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        resetPosition: Boolean,
    ) {
        super.setMediaItems(mediaItems, resetPosition)
        afterListReplaced()
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        afterListReplaced()
    }

    /**
     * "Play next" ([playNext] = true) or "Add to queue". Works in play order: with shuffle on,
     * the new tracks still play right after the current one, or after everything else.
     */
    fun insert(
        items: List<MediaItem>,
        playNext: Boolean,
    ) {
        if (items.isEmpty()) return
        val p = exoPlayer
        if (p.mediaItemCount == 0) {
            p.setMediaItems(items)
            afterListReplaced()
            p.prepare()
            return
        }
        val order = PlayOrder.of(p.currentTimeline, p.shuffleModeEnabled)
        val current = p.currentMediaItemIndex
        val at = if (playNext) current + 1 else p.mediaItemCount
        p.addMediaItems(at, items)
        if (p.shuffleModeEnabled) {
            val after = if (playNext) order.indexOf(current) else order.lastIndex
            setPlayOrder(PlayOrder.insert(order, at, items.size, after))
        }
    }

    /**
     * Encore: a copy of the current song right after it in play order, so it plays once more
     * and then the queue carries on. If that copy is already waiting, it's taken back out.
     */
    fun toggleEncore() {
        val p = exoPlayer
        val current = p.currentMediaItem ?: return
        val next = encoreAfterCurrent(p)
        if (next != null) {
            p.removeMediaItem(next)
        } else {
            insert(listOf(MediaItemMapper.encoreOf(current)), playNext = true)
        }
    }

    /** Drag-to-reorder in the queue screen, from one play position to another. */
    fun moveInPlayOrder(
        from: Int,
        to: Int,
    ) {
        val p = exoPlayer
        val count = p.mediaItemCount
        if (from !in 0 until count || to !in 0 until count || from == to) return
        if (p.shuffleModeEnabled) {
            setPlayOrder(PlayOrder.move(PlayOrder.of(p.currentTimeline, true), from, to))
        } else {
            // Shuffle off: play order is item order.
            p.moveMediaItem(from, to)
        }
    }

    /** Shuffle weighting, item by item, for [reshuffleFromCurrent]. Set by the service. */
    var shuffleWeight: (MediaItem) -> Double = { 1.0 }

    /** Replace the shuffle order with [order] (item indices in play order). */
    fun setPlayOrder(order: List<Int>) {
        if (order.size != exoPlayer.mediaItemCount) return
        exoPlayer.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
    }

    private fun afterListReplaced() {
        if (exoPlayer.shuffleModeEnabled) reshuffleFromCurrent()
    }

    private fun reshuffleFromCurrent() {
        val count = exoPlayer.mediaItemCount
        if (count == 0) return
        val weights = DoubleArray(count) { shuffleWeight(exoPlayer.getMediaItemAt(it)) }
        setPlayOrder(PlayOrder.shuffledStartingWith(exoPlayer.currentMediaItemIndex, count, weights = weights))
    }

    companion object {
        /**
         * The item index of the current song's Encore, if one is waiting right after it in
         * play order, else null.
         */
        fun encoreAfterCurrent(player: Player): Int? {
            val current = player.currentMediaItemIndex
            if (current == C.INDEX_UNSET || player.currentTimeline.isEmpty) return null
            val next =
                player.currentTimeline.getNextWindowIndex(current, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (next == C.INDEX_UNSET) return null
            val item = player.getMediaItemAt(next)
            return next.takeIf {
                MediaItemMapper.isEncore(item) && item.mediaId == player.currentMediaItem?.mediaId
            }
        }
    }
}
