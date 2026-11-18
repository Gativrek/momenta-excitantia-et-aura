package io.github.gativrek.momenta.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.gativrek.momenta.R

/**
 * The vector backdrop drawn behind every screen. Each one is drawn in code from a few
 * lines and shapes (see Patterns.kt), so it recolors cleanly and costs nothing to ship.
 */
enum class Pattern(
    @param:StringRes val labelRes: Int,
) {
    Halo(R.string.pattern_halo),
    Fracture(R.string.pattern_fracture),
    Radar(R.string.pattern_radar),
    Bars(R.string.pattern_bars),
    Speed(R.string.pattern_speed),
    Slash(R.string.pattern_slash),
    Embers(R.string.pattern_embers),
    Rain(R.string.pattern_rain),
    Tears(R.string.pattern_tears),
    Domain(R.string.pattern_domain),
    None(R.string.pattern_none),
}

/** Corner style for cards, sheets, artwork and dialogs: cut (angular) or rounded. */
enum class Edge(
    @param:StringRes val labelRes: Int,
) {
    Sharp(R.string.edge_sharp),
    Round(R.string.edge_round),
}

/**
 * Everything the user can shape about the look. Four colors are chosen; the full Material
 * color scheme is derived from them (ThemeColors.kt), so any combination stays readable.
 *
 * @property background the base color every screen sits on
 * @property accent buttons, the playing track, sliders, the aura glow
 * @property spark a second accent, for highlights and the lower glow
 * @property patternColor the color the backdrop pattern is drawn in
 * @property patternStrength 0..1, how visible the pattern is
 * @property aura 0..1, how strong the accent glow at the top of the screen is
 */
data class ThemeSpec(
    val preset: ThemePreset,
    val pattern: Pattern,
    val background: Color,
    val accent: Color,
    val spark: Color,
    val patternColor: Color,
    val patternStrength: Float,
    val aura: Float,
    val edge: Edge,
) {
    /** True when this spec is exactly its preset, untouched. */
    val isPristine: Boolean get() = this == preset.spec

    fun encode(): String =
        listOf(
            VERSION,
            preset.name,
            pattern.name,
            background.hex(),
            accent.hex(),
            spark.hex(),
            patternColor.hex(),
            patternStrength.toString(),
            aura.toString(),
            edge.name,
        ).joinToString(SEPARATOR)

    companion object {
        private const val VERSION = "1"
        private const val SEPARATOR = "|"

        val Default: ThemeSpec get() = ThemePreset.Ragnarok.spec

        /** Inverse of [encode]. Anything unreadable falls back to the default theme, never a crash. */
        fun decode(text: String?): ThemeSpec {
            val parts = text?.split(SEPARATOR) ?: return Default
            if (parts.size != 10 || parts[0] != VERSION) return Default
            return runCatching {
                ThemeSpec(
                    preset = ThemePreset.valueOf(parts[1]),
                    pattern = Pattern.valueOf(parts[2]),
                    background = parseHex(parts[3]),
                    accent = parseHex(parts[4]),
                    spark = parseHex(parts[5]),
                    patternColor = parseHex(parts[6]),
                    patternStrength = parts[7].toFloat().coerceIn(0f, 1f),
                    aura = parts[8].toFloat().coerceIn(0f, 1f),
                    edge = Edge.valueOf(parts[9]),
                )
            }.getOrDefault(Default)
        }
    }
}

/** "#RRGGBB" (colors are always opaque). */
fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** Parses "#RRGGBB" or "RRGGBB". Throws on anything else. */
fun parseHex(text: String): Color {
    val digits = text.trim().removePrefix("#")
    require(digits.length == 6) { "Expected 6 hex digits: $text" }
    return Color(0xFF000000 or digits.toLong(16))
}

/** Like [parseHex] but null instead of throwing, for text typed by the user. */
fun parseHexOrNull(text: String): Color? = runCatching { parseHex(text) }.getOrNull()

/**
 * The built-in themes. Each one is drawn from a piece of music the app was made for; the
 * doc comment on each one says which, and why it looks the way it does.
 */
enum class ThemePreset(
    @param:StringRes val labelRes: Int,
    private val pattern: Pattern,
    private val background: Long,
    private val accent: Long,
    private val spark: Long,
    private val patternColor: Long,
    private val edge: Edge,
    private val patternStrength: Float = 0.5f,
    private val aura: Float = 0.6f,
) {
    /**
     * END OF RAGNAROK, Granblue Fantasy Relink. The default. A dawn rising on a night sky in
     * the gold of going home; the dragon's crimson stays as the second accent, accepted, not erased.
     */
    Ragnarok(R.string.preset_ragnarok, Pattern.Halo, 0xFF0A0F24, 0xFFF2C14E, 0xFFC9506F, 0xFFF2C14E, Edge.Round),

    /** ...And Justice for All: cold stone, patina, the cracked statue. */
    Justice(
        R.string.preset_justice,
        Pattern.Fracture,
        0xFF141617,
        0xFF8FC1A9,
        0xFFE9E4D8,
        0xFFB9C2C0,
        Edge.Sharp,
        aura = 0.35f,
    ),

    /** Rust in Peace: Hangar 18's midnight blue, rust orange and radar green. */
    Hangar(R.string.preset_hangar, Pattern.Radar, 0xFF0B1330, 0xFFE0662F, 0xFF38E1C6, 0xFF38E1C6, Edge.Sharp),

    /** Kendrick Lamar: black, white and one red. The pattern is literally bars. */
    Bars(R.string.preset_bars, Pattern.Bars, 0xFF0F0F0F, 0xFFE3312B, 0xFFF1EEE8, 0xFFF1EEE8, Edge.Sharp, 0.4f, 0.3f),

    /** Neon White: a bright heaven, hot pink and cyan, speed lines. The one light theme. */
    Heaven(R.string.preset_heaven, Pattern.Speed, 0xFFF5F3FB, 0xFFD81B7A, 0xFF0097C7, 0xFF0097C7, Edge.Round),

    /** BlazBlue Centralfiction: black and crimson, cut through with azure. */
    Azure(R.string.preset_azure, Pattern.Slash, 0xFF110A0E, 0xFFD4243B, 0xFF3D7BFF, 0xFF3D7BFF, Edge.Sharp),

    /** Calamity: brimstone flame and profaned gold, embers rising. */
    Brimstone(
        R.string.preset_brimstone,
        Pattern.Embers,
        0xFF140707,
        0xFFFF5B22,
        0xFFFFC53D,
        0xFFFF5B22,
        Edge.Sharp,
        aura = 0.7f,
    ),

    /** Risk of Rain: storm teal and alien violet, rain that never stops. */
    Monsoon(R.string.preset_monsoon, Pattern.Rain, 0xFF0C1519, 0xFF4FD6C4, 0xFFB18CFF, 0xFF8FB7C4, Edge.Round),

    /** The Binding of Isaac: a basement, blood red and tears. */
    Basement(
        R.string.preset_basement,
        Pattern.Tears,
        0xFF1A120E,
        0xFFC0392B,
        0xFF8FC9EA,
        0xFF8FC9EA,
        Edge.Round,
        aura = 0.45f,
    ),

    /** AIZO / Jujutsu Kaisen: cursed violet and blood, a barrier closing in. */
    Domain(R.string.preset_domain, Pattern.Domain, 0xFF0B0B14, 0xFF7B6CFF, 0xFFE0303A, 0xFF7B6CFF, Edge.Sharp),
    ;

    val spec: ThemeSpec
        get() =
            ThemeSpec(
                preset = this,
                pattern = pattern,
                background = Color(background),
                accent = Color(accent),
                spark = Color(spark),
                patternColor = Color(patternColor),
                patternStrength = patternStrength,
                aura = aura,
                edge = edge,
            )
}
