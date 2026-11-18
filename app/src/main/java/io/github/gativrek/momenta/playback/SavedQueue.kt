package io.github.gativrek.momenta.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.Player
import kotlinx.coroutines.flow.first

/**
 * Everything needed to put the player back the way it was after a force-stop or reboot. Track IDs are
 * MediaStore IDs in item order; [playOrder] is only meaningful when [shuffle] is on.
 */
data class SavedQueue(
    val trackIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playOrder: List<Int> = trackIds.indices.toList(),
) {
    /**
     * This queue minus tracks that no longer exist (deleted, moved, or the permission was
     * revoked), or null if nothing is left. If the current track is gone, playback resumes
     * at the start of the next surviving track in play order.
     */
    fun keepOnly(available: Set<Long>): SavedQueue? {
        val kept = trackIds.indices.filter { trackIds[it] in available }
        if (kept.isEmpty()) return null
        // Old item index -> new item index, for the survivors.
        val remap = kept.withIndex().associate { (new, old) -> old to new }
        val order = playOrder.filter { it in remap }.map { remap.getValue(it) }
        val newIndex: Int
        val newPosition: Long
        if (index in remap) {
            newIndex = remap.getValue(index)
            newPosition = positionMs
        } else {
            val playPosition = playOrder.indexOf(index)
            val next = playOrder.drop(playPosition + 1).firstOrNull { it in remap }
            newIndex = next?.let(remap::getValue) ?: order.first()
            newPosition = 0L
        }
        return copy(
            trackIds = kept.map(trackIds::get),
            index = newIndex,
            positionMs = newPosition,
            playOrder = if (order.size == kept.size) order else kept.indices.toList(),
        )
    }

    companion object {
        fun encodeList(values: List<Number>): String = values.joinToString(",")

        fun decodeLongs(text: String?): List<Long>? =
            text?.takeIf { it.isNotEmpty() }?.split(',')?.map { it.toLongOrNull() ?: return null }

        fun decodeInts(text: String?): List<Int>? =
            text?.takeIf { it.isNotEmpty() }?.split(',')?.map { it.toIntOrNull() ?: return null }
    }
}

/**
 * Playback state in Preferences DataStore. Small and rewritten often (on pause, on track
 * change, every few seconds while playing), which is exactly what DataStore is for.
 * Excluded from backup and device transfer: MediaStore IDs mean nothing on another phone.
 */
class PlaybackStateStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun load(): SavedQueue? {
        val prefs = dataStore.data.first()
        val ids = SavedQueue.decodeLongs(prefs[TRACK_IDS]) ?: return null
        val order = SavedQueue.decodeInts(prefs[PLAY_ORDER])?.takeIf { it.sorted() == ids.indices.toList() }
        return SavedQueue(
            trackIds = ids,
            index = (prefs[INDEX] ?: 0).coerceIn(ids.indices),
            positionMs = (prefs[POSITION] ?: 0L).coerceAtLeast(0L),
            shuffle = prefs[SHUFFLE] ?: false,
            repeatMode = prefs[REPEAT] ?: Player.REPEAT_MODE_OFF,
            playOrder = order ?: ids.indices.toList(),
        )
    }

    suspend fun save(queue: SavedQueue) {
        dataStore.edit {
            it[TRACK_IDS] = SavedQueue.encodeList(queue.trackIds)
            it[INDEX] = queue.index
            it[POSITION] = queue.positionMs
            it[SHUFFLE] = queue.shuffle
            it[REPEAT] = queue.repeatMode
            it[PLAY_ORDER] = SavedQueue.encodeList(queue.playOrder)
        }
    }

    private companion object {
        val TRACK_IDS = stringPreferencesKey("track_ids")
        val INDEX = intPreferencesKey("index")
        val POSITION = longPreferencesKey("position_ms")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = intPreferencesKey("repeat_mode")
        val PLAY_ORDER = stringPreferencesKey("play_order")
    }
}
