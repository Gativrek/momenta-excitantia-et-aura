package io.github.gativrek.momenta.data.tags

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class Id3v23Test {
    private fun syncsafe(v: Int) = ByteArray(4) { k -> ((v ushr (7 * (3 - k))) and 0x7F).toByte() }

    private fun frame24(
        id: String,
        body: ByteArray,
        status: Int = 0,
        format: Int = 0,
    ) = id.toByteArray(Charsets.ISO_8859_1) + syncsafe(body.size) + byteArrayOf(status.toByte(), format.toByte()) + body

    private fun tag24(
        vararg frames: ByteArray,
        padding: Int = 64,
    ): ByteArray {
        val content = ByteArrayOutputStream().apply { frames.forEach { write(it) } }.toByteArray()
        val size = content.size + padding
        return byteArrayOf(
            'I'.code.toByte(),
            'D'.code.toByte(),
            '3'.code.toByte(),
            4,
            0,
            0,
        ) + syncsafe(size) + content +
            ByteArray(padding)
    }

    private fun utf8(s: String) = byteArrayOf(3) + s.toByteArray(Charsets.UTF_8)

    /** Frames of a v2.3 tag as id to body. */
    private fun frames23(tag: ByteArray): List<Pair<String, ByteArray>> {
        val out = mutableListOf<Pair<String, ByteArray>>()
        var i = 10
        while (i + 10 <= tag.size && tag[i].toInt() != 0) {
            val id = String(tag, i, 4, Charsets.ISO_8859_1)
            val size =
                ((tag[i + 4].toInt() and 0xFF) shl 24) or ((tag[i + 5].toInt() and 0xFF) shl 16) or
                    ((tag[i + 6].toInt() and 0xFF) shl 8) or (tag[i + 7].toInt() and 0xFF)
            out += id to tag.copyOfRange(i + 10, i + 10 + size)
            i += 10 + size
        }
        return out
    }

    @Test
    fun `year moves back to TYER and size is unchanged`() {
        val tag =
            tag24(
                frame24("TIT2", byteArrayOf(0) + "Eye".toByteArray()),
                frame24(
                    "TDRC",
                    byteArrayOf(0) + "2022".toByteArray(),
                ),
            )
        val out = Id3v23.downgrade(tag)
        assertNotNull(out)
        out!!
        assertEquals(tag.size, out.size)
        assertEquals(3, Id3v23.version(out))
        assertEquals(tag.size, Id3v23.tagLength(out))
        val frames = frames23(out)
        assertEquals(listOf("TIT2", "TYER"), frames.map { it.first })
        assertArrayEquals(byteArrayOf(0) + "2022".toByteArray(), frames[1].second)
    }

    @Test
    fun `full date also gives TDAT`() {
        val out = Id3v23.downgrade(tag24(frame24("TDRC", byteArrayOf(0) + "1988-09-07".toByteArray())))!!
        val frames = frames23(out)
        assertEquals(listOf("TYER", "TDAT"), frames.map { it.first })
        assertArrayEquals(byteArrayOf(0) + "0709".toByteArray(), frames[1].second)
    }

    @Test
    fun `utf8 text becomes latin1 or utf16`() {
        val out =
            Id3v23.downgrade(
                tag24(frame24("TALB", utf8("...And Justice for All")), frame24("TPE1", utf8("King Gnu 常田"))),
            )!!
        val frames = frames23(out)
        assertArrayEquals(byteArrayOf(0) + "...And Justice for All".toByteArray(Charsets.ISO_8859_1), frames[0].second)
        assertEquals(1, frames[1].second[0].toInt())
        assertEquals("King Gnu 常田", String(frames[1].second, 1, frames[1].second.size - 1, Charsets.UTF_16))
    }

    @Test
    fun `multiple values are joined with a slash`() {
        val out = Id3v23.downgrade(tag24(frame24("TPE1", utf8("A\u0000B"))))!!
        assertArrayEquals(byteArrayOf(0) + "A/B".toByteArray(), frames23(out).single().second)
    }

    @Test
    fun `other frames are copied byte for byte`() {
        val picture = byteArrayOf(0) + "image/jpeg".toByteArray() + byteArrayOf(0, 3, 0) + ByteArray(40) { it.toByte() }
        val out = Id3v23.downgrade(tag24(frame24("APIC", picture)))!!
        assertArrayEquals(picture, frames23(out).single().second)
    }

    @Test
    fun `gives up on what it can't express`() {
        assertNull(Id3v23.downgrade(tag24(frame24("TMOO", utf8("Calm")))))
        assertNull(Id3v23.downgrade(tag24(frame24("TIT2", utf8("x"), format = 0x01))))
        assertNull(Id3v23.downgrade(tag24(frame24("COMM", utf8("engxx")))))
        // No padding left for UTF-8 → UTF-16 growth.
        assertNull(Id3v23.downgrade(tag24(frame24("TIT2", utf8("ąęąę")), padding = 0)))
    }
}
