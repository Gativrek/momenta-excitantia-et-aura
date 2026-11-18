package io.github.gativrek.momenta.data.listening

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A song the app has a history with: the one row everything personal about a song hangs
 * off (plays now; Moments, notes and pinned themes later).
 *
 * Keyed by a file fingerprint, not just the MediaStore ID, because IDs change after a rescan,
 * a remount or a move to a new phone: the same matching rules as playlists find it again
 * (see ListeningDao.findSong). Title, artist and album are the tags as last heard, so stats
 * can still name a song whose file is gone.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("media_store_id"),
        Index("relative_path", "display_name"),
        Index("display_name"),
    ],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val title: String,
    val artist: String?,
    val album: String?,
)

/**
 * One listen. [listenedMs] is time actually playing (pauses don't count, seeking doesn't
 * add), so "how long did I listen" is a plain SUM. [completed] means the song reached its
 * end (or looped on repeat-one); a short listen that didn't complete is a skip.
 */
@Entity(
    tableName = "plays",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("song_id"), Index("started_at")],
)
data class PlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: Long,
    /** Wall-clock time the listen began, epoch milliseconds. */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
    val completed: Boolean,
)
