package io.github.gativrek.momenta.ui.format

import java.util.Locale

/** "3:05" for under an hour, "1:02:03" above. Negative input is treated as 0. */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    // Locale.ROOT: a clock-style duration, same digits and separators everywhere.
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
