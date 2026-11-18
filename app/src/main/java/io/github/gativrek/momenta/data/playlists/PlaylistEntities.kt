package io.github.gativrek.momenta.data.playlists

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * One song in a playlist. The same song may appear more than once, so an entry has its
 * own ID. [position] is the explicit order (0-based, no gaps).
 *
 * The MediaStore ID alone isn't enough to find the file again (IDs change after
 * a rescan or remount), so the entry keeps a fingerprint of the file: folder, file name,
 * duration and size. [title] and [artist] are only for showing a missing entry.
 */
@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlist_id", "position")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val position: Int,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val title: String,
    val artist: String?,
)

/** A row of the playlists list: the playlist plus what the list shows about its entries. */
data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
    /** MediaStore ID of the first entry, for the cover. */
    @ColumnInfo(name = "first_media_store_id") val firstMediaStoreId: Long?,
)
