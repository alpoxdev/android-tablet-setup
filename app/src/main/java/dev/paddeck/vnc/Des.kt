package dev.paddeck.vnc

/**
 * VNC 인증에 쓰는 DES ECB 구현입니다.
 * 자바 표준 라이브러리에 DES가 있지만, 안드로이드에서도 동일하게 돌도록 직접 구현했습니다.
 */
object Des {
    private val IP = intArrayOf(
        58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4,
        62, 54, 46, 38, 30, 22, 14, 6, 64, 56, 48, 40, 32, 24, 16, 8,
        57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
        61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
    )
    private val FP = intArrayOf(
        40, 8, 48, 16, 56, 24, 64, 32, 39, 7, 47, 15, 55, 23, 63, 31,
        38, 6, 46, 14, 54, 22, 62, 30, 37, 5, 45, 13, 53, 21, 61, 29,
        36, 4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27,
        34, 2, 42, 10, 50, 18, 58, 26, 33, 1, 41, 9, 49, 17, 57, 25,
    )
    private val E = intArrayOf(
        32, 1, 2, 3, 4, 5, 4, 5, 6, 7, 8, 9, 8, 9, 10, 11,
        12, 13, 12, 13, 14, 15, 16, 17, 16, 17, 18, 19, 20, 21, 20, 21,
        22, 23, 24, 25, 24, 25, 26, 27, 28, 29, 28, 29, 30, 31, 32, 1,
    )
    private val P = intArrayOf(
        16, 7, 20, 21, 29, 12, 28, 17, 1, 15, 23, 26, 5, 18, 31, 10,
        2, 8, 24, 14, 32, 27, 3, 9, 19, 13, 30, 6, 22, 11, 4, 25,
    )
    private val PC1 = intArrayOf(
        57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2,
        59, 51, 43, 35, 27, 19, 11, 3, 60, 52, 44, 36, 63, 55, 47, 39,
        31, 23, 15, 7, 62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37,
        29, 21, 13, 5, 28, 20, 12, 4,
    )
    private val PC2 = intArrayOf(
        14, 17, 11, 24, 1, 5, 3, 28, 15, 6, 21, 10, 23, 19, 12, 4,
        26, 8, 16, 7, 27, 20, 13, 2, 41, 52, 31, 37, 47, 55, 30, 40,
        51, 45, 33, 48, 44, 49, 39, 56, 34, 53, 46, 42, 50, 36, 29, 32,
    )
    private val SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val SBOX = arrayOf(
        intArrayOf(
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
        ),
        intArrayOf(
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
        ),
        intArrayOf(
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
        ),
        intArrayOf(
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
        ),
        intArrayOf(
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
        ),
        intArrayOf(
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
        ),
        intArrayOf(
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
        ),
        intArrayOf(
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
        ),
    )

    /** 8바이트 키로 8바이트 블록을 ECB 암호화합니다. */
    fun encrypt(key: ByteArray, block: ByteArray): ByteArray {
        require(key.size == 8) { "DES 키는 8바이트여야 합니다" }
        require(block.size == 8) { "DES 블록은 8바이트여야 합니다" }
        val subkeys = subkeys(key)
        var input = 0L
        for (i in 0 until 8) input = (input shl 8) or (block[i].toLong() and 0xFF)
        var permuted = permute(input, IP, 64)

        var left = (permuted ushr 32).toInt()
        var right = permuted.toInt()
        for (round in 0 until 16) {
            val f = feistel(right, subkeys[round])
            val next = left xor f
            left = right
            right = next
        }
        val preoutput = (right.toLong() and 0xFFFFFFFFL shl 32) or (left.toLong() and 0xFFFFFFFFL)
        val out = permute(preoutput, FP, 64)
        return ByteArray(8) { i -> ((out ushr (56 - 8 * i)) and 0xFF).toByte() }
    }

    private fun subkeys(key: ByteArray): LongArray {
        var k = 0L
        for (i in 0 until 8) k = (k shl 8) or (key[i].toLong() and 0xFF)
        val permuted = permute(k, PC1, 64)
        var c = (permuted ushr 28).toInt()
        var d = permuted.toInt() and 0x0FFFFFFF
        val out = LongArray(16)
        for (i in 0 until 16) {
            repeat(SHIFTS[i]) {
                c = rotate28(c)
                d = rotate28(d)
            }
            val cd = (c.toLong() shl 28) or (d.toLong() and 0xFFFFFFFL)
            out[i] = permute(cd, PC2, 56)
        }
        return out
    }

    private fun rotate28(v: Int): Int = ((v shl 1) or (v ushr 27)) and 0x0FFFFFFF

    private fun feistel(right: Int, subkey: Long): Int {
        val expanded = permute(right.toLong() and 0xFFFFFFFFL, E, 32)
        val x = expanded xor subkey
        var s = 0
        for (i in 0 until 8) {
            val chunk = ((x ushr (42 - 6 * i)) and 0x3F).toInt()
            val row = ((chunk and 0x20) ushr 4) or (chunk and 1)
            val col = (chunk ushr 1) and 0x0F
            s = (s shl 4) or SBOX[i][row * 16 + col]
        }
        return permute(s.toLong() and 0xFFFFFFFFL, P, 32).toInt()
    }

    private fun permute(value: Long, table: IntArray, inBits: Int): Long {
        var out = 0L
        for (pos in table) {
            val bit = (value ushr (inBits - pos)) and 1L
            out = (out shl 1) or bit
        }
        return out
    }

    /** VNC 비밀번호를 DES 키로 변환합니다. 각 바이트의 비트 순서를 뒤집습니다. */
    fun vncKey(password: String): ByteArray {
        val raw = ByteArray(8)
        val bytes = password.toByteArray(Charsets.US_ASCII)
        for (i in 0 until minOf(8, bytes.size)) raw[i] = bytes[i]
        return ByteArray(8) { i -> reverseBits(raw[i]) }
    }

    private fun reverseBits(b: Byte): Byte {
        var v = b.toInt() and 0xFF
        var r = 0
        repeat(8) {
            r = (r shl 1) or (v and 1)
            v = v ushr 1
        }
        return r.toByte()
    }
}
