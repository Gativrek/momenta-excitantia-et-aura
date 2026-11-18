package io.github.gativrek.momenta.data.moments

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.gativrek.momenta.data.listening.SongEntity

/**
 * A Moment: the part of a song that hits. A song may have several (each voice of a duet,
 * each peak of a boss theme), each with its own settings from the lightning-gear.
 */
@Entity(
    tableName = "moments",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("song_id")],
)
data class MomentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    /** Optional, e.g. "the dragon's verse". */
    val name: String? = null,
    /** How much of the build-up plays before the Moment in Moments mode and Jump to the Moment. */
    @ColumnInfo(name = "lead_in_ms") val leadInMs: Long = Moments.DEFAULT_LEAD_IN_MS,
    /** Volume boost while the Moment plays, 0 to [Moments.MAX_BOOST_PERCENT]. */
    @ColumnInfo(name = "boost_percent") val boostPercent: Int = 0,
    /** Times the Moment was heard all the way through. */
    @ColumnInfo(name = "heard_count") val heardCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    val durationMs: Long get() = endMs - startMs

    /** Where playback starts to hear this Moment with its build-up. */
    val runUpStartMs: Long get() = (startMs - leadInMs).coerceAtLeast(0)

    operator fun contains(positionMs: Long) = positionMs in startMs until endMs
}

/** A Moment with the song row it hangs off, to find the song's current file. */
data class MomentRow(
    @Embedded val moment: MomentEntity,
    @Embedded(prefix = "s_") val song: SongEntity,
)

object Moments {
    const val MAX_BOOST_PERCENT = 30
    const val DEFAULT_LEAD_IN_MS = 8_000L
    const val MAX_LEAD_IN_MS = 20_000L
    const val MIN_DURATION_MS = 1_000L

    /** Saving at the hit: the build-up is usually just before, the payoff just after. */
    const val SAVE_BEFORE_MS = 5_000L
    const val SAVE_AFTER_MS = 10_000L

    /**
     * The window saved when ⚡ is pressed at [positionMs]: a little before (the press always
     * comes late) to a little after, kept inside the song.
     */
    fun windowAt(
        positionMs: Long,
        durationMs: Long,
    ): LongRange {
        val end = if (durationMs > 0) durationMs else Long.MAX_VALUE
        val start = (positionMs - SAVE_BEFORE_MS).coerceIn(0, (end - MIN_DURATION_MS).coerceAtLeast(0))
        val stop = (positionMs + SAVE_AFTER_MS).coerceAtMost(end).coerceAtLeast(start + MIN_DURATION_MS)
        return start..stop
    }

    /** [percent] of extra loudness as LoudnessEnhancer gain, in millibels. +30% is about +2.3 dB. */
    fun boostGainMb(percent: Int): Int =
        if (percent <= 0) 0 else (2000 * kotlin.math.log10(1 + percent / 100.0)).toInt()
}
