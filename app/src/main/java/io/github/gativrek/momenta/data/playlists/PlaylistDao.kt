package io.github.gativrek.momenta.data.playlists

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name,
            (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlist_id = p.id) AS entry_count,
            (SELECT e.media_store_id FROM playlist_entries e WHERE e.playlist_id = p.id
                ORDER BY e.position LIMIT 1) AS first_media_store_id
        FROM playlists p
        ORDER BY p.name COLLATE NOCASE, p.id
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun entries(playlistId: Long): List<PlaylistEntryEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
        now: Long,
    )

    /** Entries go with it (foreign key CASCADE). */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("UPDATE playlists SET updated_at = :now WHERE id = :id")
    suspend fun touch(
        id: Long,
        now: Long,
    )

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Update
    suspend fun updateEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    /** Append [entries] (their positions are assigned here) in one transaction. */
    @Transaction
    suspend fun append(
        playlistId: Long,
        entries: List<PlaylistEntryEntity>,
        now: Long,
    ) {
        val start = nextPosition(playlistId)
        insertEntries(entries.mapIndexed { i, e -> e.copy(id = 0, playlistId = playlistId, position = start + i) })
        touch(playlistId, now)
    }

    /** Move the entry at [from] to [to], renumbering everything in one transaction. */
    @Transaction
    suspend fun move(
        playlistId: Long,
        from: Int,
        to: Int,
        now: Long,
    ) {
        val current = entries(playlistId)
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        updateEntries(renumbered(reordered))
        touch(playlistId, now)
    }

    /** Remove one entry and close the gap it leaves. */
    @Transaction
    suspend fun remove(
        playlistId: Long,
        entryId: Long,
        now: Long,
    ) {
        deleteEntry(entryId)
        updateEntries(renumbered(entries(playlistId)))
        touch(playlistId, now)
    }

    private fun renumbered(entries: List<PlaylistEntryEntity>): List<PlaylistEntryEntity> =
        entries.mapIndexedNotNull { i, e -> if (e.position == i) null else e.copy(position = i) }
}
