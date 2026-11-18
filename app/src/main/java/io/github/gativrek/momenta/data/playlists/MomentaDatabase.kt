package io.github.gativrek.momenta.data.playlists

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.gativrek.momenta.data.listening.ListeningDao
import io.github.gativrek.momenta.data.listening.PlayEntity
import io.github.gativrek.momenta.data.listening.SongEntity
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.MomentsDao
import io.github.gativrek.momenta.data.notes.LinerNoteEntity
import io.github.gativrek.momenta.data.notes.LinerNotesDao

/**
 * The app's only database: playlists, listening history, Moments and liner notes. The library itself is never copied here
 * (MediaStore is the source of truth). Schemas are exported to app/schemas/ so every
 * future version ships with a real migration, never a destructive one.
 */
@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        SongEntity::class,
        PlayEntity::class,
        MomentEntity::class,
        LinerNoteEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // New tables only, so Room derives each migration. 2: listening history (songs, plays). 3: moments.
    // 4: liner notes.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class MomentaDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    abstract fun listeningDao(): ListeningDao

    abstract fun momentsDao(): MomentsDao

    abstract fun linerNotesDao(): LinerNotesDao

    companion object {
        fun create(context: Context): MomentaDatabase =
            Room
                .databaseBuilder(context.applicationContext, MomentaDatabase::class.java, "momenta.db")
                .build()
    }
}
