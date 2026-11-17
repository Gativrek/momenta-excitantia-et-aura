package io.github.gativrek.momenta.data.library

import java.text.Collator
import java.util.Locale

/**
 * Pure helpers that turn raw MediaStore column values into [Track] fields.
 * Kept free of Android types so they run in JVM unit tests.
 */
internal object TrackFields {
    /** MediaStore's placeholder for a missing artist/album tag. */
    private const val UNKNOWN = "<unknown>"

    /** Matches the storage-volume prefix of a legacy absolute path, e.g. "/storage/emulated/0/". */
    private val volumePrefix = Regex("^(/storage/emulated/\\d+/|/storage/[^/]+/|/sdcard/|/mnt/sdcard/)")

    /** Null for blank or "<unknown>" tags, so the UI can show a localized "Unknown artist". */
    fun tagOrNull(raw: String?): String? = raw?.trim()?.takeUnless { it.isEmpty() || it == UNKNOWN }

    /** Title tag, falling back to the file name without its extension. */
    fun title(
        raw: String?,
        displayName: String,
    ): String = tagOrNull(raw) ?: displayName.substringBeforeLast('.', displayName)

    /**
     * MediaStore's TRACK column packs the disc into the thousands: 2005 = disc 2, track 5.
     * Returns (disc, track), each null when absent.
     */
    fun discAndTrack(raw: Int?): Pair<Int?, Int?> {
        if (raw == null || raw <= 0) return null to null
        val disc = (raw / 1000).takeIf { it > 0 }
        val track = (raw % 1000).takeIf { it > 0 }
        return disc to track
    }

    fun year(raw: Int?): Int? = raw?.takeIf { it > 0 }

    /** API 29+: RELATIVE_PATH is already "Music/Foo/"; normalise the trailing slash. */
    fun folderFromRelativePath(raw: String?): String {
        val trimmed = raw?.trim().orEmpty().trim('/')
        return if (trimmed.isEmpty()) "" else "$trimmed/"
    }

    /**
     * API 26–28: derive the same shape from the deprecated absolute DATA path,
     * e.g. "/storage/emulated/0/Music/Foo/song.mp3" -> "Music/Foo/".
     */
    fun folderFromDataPath(raw: String?): String {
        val path = raw?.trim().orEmpty()
        if (path.isEmpty()) return ""
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        return folderFromRelativePath(volumePrefix.replace("$parent/", ""))
    }

    /**
     * What a title sorts by: leading punctuation and symbols are skipped, so
     * "... And Justice for All" files under A and "[Live] Foo" under L.
     * A title made only of symbols keeps them, so it still sorts somewhere stable.
     */
    fun sortKey(title: String): String = title.trimStart { !it.isLetterOrDigit() }.ifEmpty { title }

    /**
     * Songs-list order: by [sortKey], using the device locale's collation, so case and
     * accents sort like a person expects ("árbol" next to "arbol", not after "zebra").
     */
    fun titleComparator(locale: Locale = Locale.getDefault()): Comparator<Track> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        return compareBy(collator) { track: Track -> sortKey(track.title) }.thenBy { it.id }
    }
}
