package io.github.gativrek.momenta.data.listening

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.playlists.TrackMatcher

@Dao
interface ListeningDao {
    @Query("SELECT * FROM songs WHERE media_store_id = :mediaStoreId AND display_name = :displayName LIMIT 1")
    suspend fun songById(
        mediaStoreId: Long,
        displayName: String,
    ): SongEntity?

    @Query("SELECT * FROM songs WHERE relative_path = :relativePath AND display_name = :displayName LIMIT 1")
    suspend fun songByPath(
        relativePath: String,
        displayName: String,
    ): SongEntity?

    @Query(
        """
        SELECT * FROM songs WHERE display_name = :displayName AND size_bytes = :sizeBytes
            AND ABS(duration_ms - :durationMs) <= :toleranceMs
        LIMIT 1
        """,
    )
    suspend fun songByFingerprint(
        displayName: String,
        sizeBytes: Long,
        durationMs: Long,
        toleranceMs: Long,
    ): SongEntity?

    @Insert
    suspend fun insertSong(song: SongEntity): Long

    @Update
    suspend fun updateSong(song: SongEntity)

    @Insert
    suspend fun insertPlay(play: PlayEntity): Long

    @Query("SELECT COUNT(*) FROM plays")
    suspend fun playCount(): Int

    /**
     * The song row for [track], found by the same rules playlists use (same ID and file name;
     * same folder and file name; same file name, size and duration), created if new. The row
     * is refreshed to the track as it is now, so the next lookup is a direct hit.
     */
    @Transaction
    suspend fun songFor(track: Track): Long {
        val found =
            songById(track.id, track.displayName)
                ?: songByPath(track.folderPath, track.displayName)
                ?: songByFingerprint(
                    track.displayName,
                    track.sizeBytes,
                    track.durationMs,
                    TrackMatcher.DURATION_TOLERANCE_MS,
                )
        val fresh = songEntity(track)
        if (found == null) return insertSong(fresh)
        val updated = fresh.copy(id = found.id)
        if (updated != found) updateSong(updated)
        return found.id
    }

    @Transaction
    suspend fun record(
        track: Track,
        play: PlayEntity,
    ) {
        insertPlay(play.copy(songId = songFor(track)))
    }
}

internal fun songEntity(track: Track) =
    SongEntity(
        mediaStoreId = track.id,
        relativePath = track.folderPath,
        displayName = track.displayName,
        durationMs = track.durationMs,
        sizeBytes = track.sizeBytes,
        title = track.title,
        artist = track.artist,
        album = track.album,
    )
