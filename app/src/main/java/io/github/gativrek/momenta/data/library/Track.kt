package io.github.gativrek.momenta.data.library

/**
 * One audio file from MediaStore. Pure Kotlin (no Android types) so sorting, grouping and
 * matching logic can be unit-tested on the JVM. Get its playable URI with [Track.contentUri].
 */
data class Track(
    /** MediaStore `_ID`. Not stable forever: see KICKOFF.md "Playlists". */
    val id: Long,
    val title: String,
    /** Null when the file has no artist tag (MediaStore reports "<unknown>"). */
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val artistId: Long,
    val durationMs: Long,
    /** Track number within its disc, or null if untagged. */
    val trackNumber: Int?,
    /** Disc number, or null if untagged. */
    val discNumber: Int?,
    val year: Int?,
    /** Folder relative to its storage volume, with a trailing slash, e.g. "Music/Albums/Foo/". */
    val folderPath: String,
    /** File name including extension, e.g. "01 - Intro.mp3". */
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    /** Seconds since the epoch, as MediaStore stores it. */
    val dateAddedSeconds: Long,
)
