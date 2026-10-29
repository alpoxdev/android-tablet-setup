package dev.paddeck.vnc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DesTest {

    @Test
    fun encryptsKnownVector() {
        val key = hex("133457799BBCDFF1")
        val plain = hex("0123456789ABCDEF")
        val expected = hex("85E813540F0AB405")
        assertArrayEquals(expected, Des.encrypt(key, plain))
    }

    @Test
    fun vncKeyReversesBitsOfPassword() {
        val key = Des.vncKey("pass")
        assertEquals(0x0E, key[0].toInt() and 0xFF)
        assertEquals(0x86, key[1].toInt() and 0xFF)
        assertEquals(0xCE, key[2].toInt() and 0xFF)
        assertEquals(0xCE, key[3].toInt() and 0xFF)
        assertEquals(0, key[7].toInt())
    }

    @Test
    fun sameInputGivesSameOutput() {
        val key = Des.vncKey("paddeck")
        val a = Des.encrypt(key, ByteArray(8) { it.toByte() })
        val b = Des.encrypt(key, ByteArray(8) { it.toByte() })
        assertArrayEquals(a, b)
    }

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { i -> text.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
