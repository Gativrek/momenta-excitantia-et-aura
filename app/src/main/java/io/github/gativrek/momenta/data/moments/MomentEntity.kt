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
    /**
     * A video from the phone (a content URI the app holds a lasting read grant for) that plays
     * silently behind the app while the Moment hits. Null for none.
     */
    @ColumnInfo(name = "clip_uri") val clipUri: String? = null,
    /** True: the clip plays behind the whole app. False (the default): in place of the cover. */
    @ColumnInfo(name = "clip_behind", defaultValue = "0") val clipBehind: Boolean = false,
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
    /** The longest section offered as a Moment after it keeps getting replayed. */
    const val SUGGEST_MAX_MS = 30_000L

    /** Moments mode: how long a song plays on past its Moment before the next one comes in. */
    const val REEL_TAIL_MS = 3_000L

    const val MAX_BOOST_PERCENT = 50
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

    /**
     * [percent] more loudness, as a gain to multiply the audio by. Loudness, not amplitude: we
     * hear about twice as loud per +10 dB, so +50% is 10·log2(1.5) ≈ +5.8 dB, a gain of ≈ 1.96.
     * Capped at [MAX_BOOST_PERCENT] (older Moments may have been saved with more).
     */
    fun boostGain(percent: Int): Float {
        if (percent <= 0) return 1f
        val p = percent.coerceAtMost(MAX_BOOST_PERCENT)
        val db = 10 * kotlin.math.ln(1 + p / 100.0) / kotlin.math.ln(2.0)
        return Math.pow(10.0, db / 20).toFloat()
    }
}

/** A section of a song that keeps getting replayed, offered as a Moment. */
data class MomentSuggestion(
    val trackId: Long,
    val startMs: Long,
    val endMs: Long,
)
