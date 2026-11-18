package io.github.gativrek.momenta.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ThemeTest {
    @Test
    fun `every preset encodes and decodes to itself`() {
        ThemePreset.entries.forEach { preset ->
            assertEquals(preset.spec, ThemeSpec.decode(preset.spec.encode()))
        }
    }

    @Test
    fun `an edited theme survives a round trip`() {
        val edited =
            ThemePreset.Hangar.spec.copy(
                pattern = Pattern.Tears,
                accent = Color(0xFF123456),
                patternStrength = 0.25f,
                aura = 0f,
                edge = Edge.Round,
            )
        val decoded = ThemeSpec.decode(edited.encode())
        assertEquals(edited, decoded)
        assertFalse(decoded.isPristine)
    }

    @Test
    fun `unreadable stored values fall back to the default theme`() {
        listOf(null, "", "garbage", "2|Ragnarok", "1|Nope|Halo|#000000|#000000|#000000|#000000|0.5|0.5|Sharp")
            .forEach { assertEquals(ThemeSpec.Default, ThemeSpec.decode(it)) }
    }

    @Test
    fun `hex parsing`() {
        assertEquals(Color(0xFFF2C14E), parseHex("#F2C14E"))
        assertEquals(Color(0xFFF2C14E), parseHex("f2c14e"))
        assertEquals("#F2C14E", Color(0xFFF2C14E).hex())
        assertNull(parseHexOrNull("#F2C1"))
        assertNull(parseHexOrNull("#GGGGGG"))
    }

    @Test
    fun `heaven is the one light preset`() {
        assertEquals(
            listOf(ThemePreset.Heaven),
            ThemePreset.entries.filterNot { isDarkBackground(it.spec.background) },
        )
    }

    @Test
    fun `every preset is readable`() = ThemePreset.entries.forEach { assertReadable(it.spec, it.name) }

    @Test
    fun `any four colors still give readable text`() {
        val random = Random(42)

        fun color() = Color(0xFF000000 or random.nextLong(0x1000000))
        repeat(500) {
            val spec =
                ThemeSpec.Default.copy(
                    background = color(),
                    accent = color(),
                    spark = color(),
                    patternColor = color(),
                )
            assertReadable(spec, spec.encode())
        }
    }

    @Test
    fun `a readable accent is kept exactly as chosen`() {
        val spec = ThemePreset.Ragnarok.spec
        assertEquals(spec.accent, colorSchemeFor(spec).primary)
    }

    private fun assertReadable(
        spec: ThemeSpec,
        name: String,
    ) {
        val s = colorSchemeFor(spec)

        fun check(
            what: String,
            fg: Color,
            bg: Color,
            min: Double,
        ) = assertTrue("$name: $what contrast ${contrast(fg, bg)} < $min", contrast(fg, bg) >= min - 0.01)
        check("onBackground", s.onBackground, s.background, TEXT_CONTRAST)
        check("onSurfaceVariant", s.onSurfaceVariant, s.background, TEXT_CONTRAST)
        check("onSurface on container", s.onSurface, s.surfaceContainerHigh, TEXT_CONTRAST)
        check("primary", s.primary, s.background, ACCENT_CONTRAST)
        check("tertiary", s.tertiary, s.background, ACCENT_CONTRAST)
        check("onPrimary", s.onPrimary, s.primary, TEXT_CONTRAST)
        check("onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer, TEXT_CONTRAST)
        check("onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer, TEXT_CONTRAST)
    }
}
