package io.github.gativrek.momenta.data.notes

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.AlbumKey
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.ListeningDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * A liner note: a private line about a song or an album (a memory, why it matters).
 * [key] is "song:<song row ID>" (the rescan-proof song row of the listening log) or
 * "album:<AlbumKey>", so notes follow the music through rescans like Moments do.
 */
@Entity(tableName = "liner_notes")
data class LinerNoteEntity(
    @PrimaryKey val key: String,
    val text: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface LinerNotesDao {
    @Query("SELECT * FROM liner_notes")
    fun observeAll(): Flow<List<LinerNoteEntity>>

    @Upsert
    suspend fun upsert(note: LinerNoteEntity)

    @Query("DELETE FROM liner_notes WHERE `key` = :key")
    suspend fun delete(key: String)
}

/** The notes that belong to the playing song: its own, and its album's. */
data class LinerNotes(
    val song: String?,
    val album: String?,
)

class LinerNotesRepository(
    private val dao: LinerNotesDao,
    private val listeningDao: ListeningDao,
) {
    private val all: Flow<Map<String, String>> = dao.observeAll().map { notes -> notes.associate { it.key to it.text } }

    /** [track]'s own note and its album's. */
    fun notesFor(track: Track): Flow<LinerNotes> =
        flow {
            val songKey = songKey(track)
            val albumKey = ALBUM + AlbumKey.of(track)
            emitAll(all.map { LinerNotes(song = it[songKey], album = it[albumKey]) })
        }

    fun albumNote(album: Album): Flow<String?> =
        all.map { notes -> AlbumKey.of(album).firstNotNullOfOrNull { notes[ALBUM + it] } }

    /** Saves [text] as [track]'s note; blank deletes it. */
    suspend fun saveSong(
        track: Track,
        text: String,
    ) = put(listOf(songKey(track)), text)

    /** Saves [text] as [album]'s note; blank deletes it. */
    suspend fun saveAlbum(
        album: Album,
        text: String,
    ) = put(AlbumKey.of(album).map { ALBUM + it }, text)

    private suspend fun songKey(track: Track): String = SONG + listeningDao.songFor(track)

    private suspend fun put(
        keys: Collection<String>,
        text: String,
    ) {
        val trimmed = text.trim()
        val now = System.currentTimeMillis()
        keys.forEach { key ->
            if (trimmed.isEmpty()) dao.delete(key) else dao.upsert(LinerNoteEntity(key, trimmed, now))
        }
    }

    private companion object {
        const val SONG = "song:"
        const val ALBUM = "album:"
    }
}
