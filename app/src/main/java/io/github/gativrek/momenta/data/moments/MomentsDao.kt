package io.github.gativrek.momenta.data.moments

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentsDao {
    @Query(
        """
        SELECT m.*,
            s.id AS s_id, s.media_store_id AS s_media_store_id, s.relative_path AS s_relative_path,
            s.display_name AS s_display_name, s.duration_ms AS s_duration_ms, s.size_bytes AS s_size_bytes,
            s.title AS s_title, s.artist AS s_artist, s.album AS s_album
        FROM moments m JOIN songs s ON s.id = m.song_id
        ORDER BY m.start_ms
        """,
    )
    fun observeAll(): Flow<List<MomentRow>>

    @Insert
    suspend fun insert(moment: MomentEntity): Long

    @Update
    suspend fun update(moment: MomentEntity)

    @Query("DELETE FROM moments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE moments SET heard_count = heard_count + 1 WHERE id = :id")
    suspend fun countHeard(id: Long)
}
