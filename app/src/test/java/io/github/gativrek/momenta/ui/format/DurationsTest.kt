package io.github.gativrek.momenta.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {
    @Test
    fun `formats minutes and seconds`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59_999))
        assertEquals("3:05", formatDuration(185_000))
    }

    @Test
    fun `adds hours when needed`() {
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun `negative is zero`() {
        assertEquals("0:00", formatDuration(-5))
    }
}
