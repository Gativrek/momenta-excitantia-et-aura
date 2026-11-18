package io.github.gativrek.momenta.data.playlists

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's only database: playlists. The library itself is never copied here
 * (MediaStore is the source of truth). Schemas are exported to app/schemas/ so every
 * future version ships with a real migration, never a destructive one.
 */
@Database(
    entities = [PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MomentaDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun create(context: Context): MomentaDatabase =
            Room
                .databaseBuilder(context.applicationContext, MomentaDatabase::class.java, "momenta.db")
                .build()
    }
}
