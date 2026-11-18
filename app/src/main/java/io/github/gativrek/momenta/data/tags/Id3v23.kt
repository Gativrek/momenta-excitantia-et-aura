package io.github.gativrek.momenta.data.tags

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Turns an ID3v2.4 tag back into ID3v2.3, in the same number of bytes.
 *
 * TagLib always saves MP3 tags as v2.4. Android's media scanner doesn't read the v2.4 year
 * frame (TDRC), so an edited song would lose its year in the library. When the file was v2.3
 * before the edit, the tag goes back to v2.3: the year returns to TYER (plus TDAT for a day and
 * month), text written as UTF-8 becomes Latin-1 or UTF-16, and frame sizes lose their
 * syncsafe encoding. Every other frame is copied byte for byte.
 *
 * Anything this doesn't fully understand (unsynchronisation, extended headers, frame
 * compression, v2.4-only frames, or a result that no longer fits) returns null and the tag
 * stays v2.4: correct, just without the year in MediaStore.
 */
object Id3v23 {
    const val HEADER_SIZE = 10

    /** Whole tag length (header + frames + padding) from a tag header, or -1 if [header] isn't one. */
    fun tagLength(header: ByteArray): Int {
        if (header.size < HEADER_SIZE || !header.startsWith(ID3)) return -1
        val footer = if (header[3].toInt() == 4 && header[5].toInt() and FOOTER != 0) HEADER_SIZE else 0
        return HEADER_SIZE + syncsafe(header, 6) + footer
    }

    /** The major version (3 or 4) from a tag header, or -1 if [header] isn't an ID3v2 tag. */
    fun version(header: ByteArray): Int = if (header.size >= 4 && header.startsWith(ID3)) header[3].toInt() else -1

    /** [tag] is a whole v2.4 tag (see [tagLength]). Returns a v2.3 tag of exactly the same size, or null. */
    fun downgrade(tag: ByteArray): ByteArray? {
        if (version(tag) != 4 || tag[5].toInt() and 0xFF != 0) return null // unsync, ext. header, footer...
        val end = HEADER_SIZE + syncsafe(tag, 6)
        if (end > tag.size) return null
        val frames = ByteArrayOutputStream()
        var i = HEADER_SIZE
        while (i + FRAME_HEADER <= end && tag[i].toInt() != 0) {
            val id = String(tag, i, 4, Charsets.ISO_8859_1)
            val size = syncsafe(tag, i + 4)
            val status = tag[i + 8].toInt() and 0xFF
            val format = tag[i + 9].toInt() and 0xFF
            val bodyStart = i + FRAME_HEADER
            if (format != 0 || size < 0 || bodyStart + size > end || id in V24_ONLY) return null
            val body = tag.copyOfRange(bodyStart, bodyStart + size)
            // v2.4 status flags sit one bit lower than v2.3's.
            val flags = (status shl 1) and 0xE0
            val converted = convert(id, body) ?: return null
            converted.forEach { (newId, newBody) -> writeFrame(frames, newId, flags, newBody) }
            i = bodyStart + size
        }
        val content = frames.toByteArray()
        val room = tag.size - HEADER_SIZE
        if (content.size > room) return null
        return ByteArray(tag.size).also { out ->
            ID3.copyInto(out)
            out[3] = 3
            out[4] = 0
            out[5] = 0
            writeSyncsafe(out, 6, room)
            content.copyInto(out, HEADER_SIZE)
        }
    }

    private fun convert(
        id: String,
        body: ByteArray,
    ): List<Pair<String, ByteArray>>? {
        if (id == "TDRC") {
            val date = decode(body) ?: return null
            val match = DATE.matchEntire(date.trim()) ?: return null
            val (year, month, day) = match.destructured
            return buildList {
                add("TYER" to latin1Text(year))
                if (month.isNotEmpty() && day.isNotEmpty()) add("TDAT" to latin1Text(day + month))
            }
        }
        if (id.startsWith("T") && id != "TXXX") return listOf(id to text(decode(body) ?: return null))
        // Other frames TagLib copied from the v2.3 original keep v2.3-valid encodings; ones it wrote
        // as UTF-8 or UTF-16BE (encodings 3 and 2) aren't valid in v2.3.
        if (body.isNotEmpty() && id in ENCODED && body[0].toInt() !in 0..1) return null
        return listOf(id to body)
    }

    /** A text frame's value, with v2.4's NUL-separated values joined the v2.3 way. */
    private fun decode(body: ByteArray): String? {
        if (body.isEmpty()) return ""
        val charset = charsetOf(body[0].toInt()) ?: return null
        val raw = String(body, 1, body.size - 1, charset)
        return raw.split('\u0000').filter { it.isNotEmpty() }.joinToString("/")
    }

    private fun charsetOf(encoding: Int): Charset? =
        when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> null
        }

    /** Latin-1 when it fits (smaller, and what most v2.3 readers expect), UTF-16 with BOM otherwise. */
    private fun text(value: String): ByteArray =
        if (Charsets.ISO_8859_1.newEncoder().canEncode(value)) {
            latin1Text(value)
        } else {
            // Java's UTF_16 encoder writes a big-endian BOM first.
            byteArrayOf(1) + value.toByteArray(Charsets.UTF_16)
        }

    private fun latin1Text(value: String): ByteArray = byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)

    private fun writeFrame(
        out: ByteArrayOutputStream,
        id: String,
        flags: Int,
        body: ByteArray,
    ) {
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        val size = body.size
        out.write(byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()))
        out.write(byteArrayOf(flags.toByte(), 0))
        out.write(body)
    }

    private fun syncsafe(
        b: ByteArray,
        at: Int,
    ): Int {
        var v = 0
        for (k in 0 until 4) {
            val x = b[at + k].toInt() and 0xFF
            if (x and 0x80 != 0) return -1
            v = (v shl 7) or x
        }
        return v
    }

    private fun writeSyncsafe(
        b: ByteArray,
        at: Int,
        value: Int,
    ) {
        for (k in 0 until 4) b[at + k] = ((value ushr (7 * (3 - k))) and 0x7F).toByte()
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val ID3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())
    private const val FOOTER = 0x10
    private const val FRAME_HEADER = 10

    /** "2022", "2022-03", "2022-03-14", optionally with a time after it. */
    private val DATE = Regex("""(\d{4})(?:-(\d{2}))?(?:-(\d{2}))?(?:T.*)?""")

    /** Frames that have an encoding byte up front and may carry text TagLib re-encoded. */
    private val ENCODED = setOf("TXXX", "COMM", "USLT", "APIC", "WXXX", "GEOB", "SYLT", "USER", "IPLS", "COMR")

    /** Frames that exist only in v2.4: no faithful v2.3 equivalent, so leave such tags alone. */
    private val V24_ONLY =
        setOf(
            "ASPI",
            "EQU2",
            "RVA2",
            "SEEK",
            "SIGN",
            "TDEN",
            "TDOR",
            "TDRL",
            "TDTG",
            "TIPL",
            "TMCL",
            "TMOO",
            "TPRO",
            "TSST",
        )
}
