package dev.paddeck.vnc

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** RFB 상수. RFC 6143 기준입니다. */
object Enc {
    const val RAW = 0
    const val COPY_RECT = 1
    const val HEXTILE = 5
    const val ZRLE = 16
    const val DESKTOP_SIZE = -223
}

object SecType {
    const val INVALID = 0
    const val NONE = 1
    const val VNC_AUTH = 2
}

/** 서버가 알려준 픽셀 형식. */
data class PixelFormat(
    val bitsPerPixel: Int,
    val depth: Int,
    val bigEndian: Boolean,
    val trueColor: Boolean,
    val redMax: Int,
    val greenMax: Int,
    val blueMax: Int,
    val redShift: Int,
    val greenShift: Int,
    val blueShift: Int,
) {
    /** 32bpp · 24depth · RGB888 이면 3바이트 CPIXEL을 쓸 수 있습니다. */
    val canUseCompactPixel: Boolean
        get() = bitsPerPixel == 32 && depth <= 24 &&
            redMax == 255 && greenMax == 255 && blueMax == 255 &&
            ((redShift == 16 && greenShift == 8 && blueShift == 0) ||
                (redShift == 0 && greenShift == 8 && blueShift == 16))

    fun toArgb(raw: Int): Int {
        val r = (raw shr redShift) and redMax
        val g = (raw shr greenShift) and greenMax
        val b = (raw shr blueShift) and blueMax
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        /** 우리가 요청하는 형식: 리틀엔디언 ARGB8888. */
        val REQUESTED = PixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0)
    }
}

internal class RfbInput(input: InputStream) {
    private val stream = input
    private val small = ByteArray(8)

    fun u8(): Int {
        val v = stream.read()
        if (v < 0) throw EOFException("서버가 연결을 닫았습니다")
        return v
    }

    fun u16(): Int {
        readFully(small, 2)
        return ((small[0].toInt() and 0xFF) shl 8) or (small[1].toInt() and 0xFF)
    }

    fun u32(): Long {
        readFully(small, 4)
        return ((small[0].toLong() and 0xFF) shl 24) or
            ((small[1].toLong() and 0xFF) shl 16) or
            ((small[2].toLong() and 0xFF) shl 8) or
            (small[3].toLong() and 0xFF)
    }

    fun i32(): Int = u32().toInt()

    fun bytes(n: Int): ByteArray {
        val out = ByteArray(n)
        readFully(out, n)
        return out
    }

    fun skip(n: Int) {
        var left = n
        while (left > 0) {
            val read = stream.read(small, 0, minOf(left, small.size))
            if (read < 0) throw EOFException("서버가 연결을 닫았습니다")
            left -= read
        }
    }

    fun readFully(dst: ByteArray, n: Int) {
        var off = 0
        while (off < n) {
            val read = stream.read(dst, off, n - off)
            if (read < 0) throw EOFException("서버가 연결을 닫았습니다")
            off += read
        }
    }

    /** 문자열을 읽고 UTF-8로 해석합니다. */
    fun string(n: Int): String = String(bytes(n), Charsets.UTF_8)
}

internal class RfbOutput(out: OutputStream) {
    private val stream = out
    private val small = ByteArray(4)

    fun u8(v: Int) {
        stream.write(v and 0xFF)
    }

    fun u16(v: Int) {
        small[0] = ((v shr 8) and 0xFF).toByte()
        small[1] = (v and 0xFF).toByte()
        stream.write(small, 0, 2)
    }

    fun u32(v: Int) {
        small[0] = ((v ushr 24) and 0xFF).toByte()
        small[1] = ((v ushr 16) and 0xFF).toByte()
        small[2] = ((v ushr 8) and 0xFF).toByte()
        small[3] = (v and 0xFF).toByte()
        stream.write(small, 0, 4)
    }

    fun bytes(b: ByteArray) = stream.write(b)

    fun flush() = stream.flush()
}

/** 화면 한 장. 픽셀은 ARGB8888. */
class Framebuffer(var width: Int, var height: Int) {
    var pixels: IntArray = IntArray(width * height)
        private set

    fun resize(w: Int, h: Int) {
        width = w
        height = h
        pixels = IntArray(w * h)
    }

    fun set(x: Int, y: Int, argb: Int) {
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] = argb
    }

    fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        val x0 = maxOf(0, x)
        val y0 = maxOf(0, y)
        val x1 = minOf(width, x + w)
        val y1 = minOf(height, y + h)
        if (x0 >= x1 || y0 >= y1) return
        for (row in y0 until y1) {
            val start = row * width + x0
            pixels.fill(argb, start, start + (x1 - x0))
        }
    }

    fun copyFrom(src: Framebuffer, sx: Int, sy: Int, w: Int, h: Int) {
        // 겹칠 수 있으므로 줄 단위로 복사합니다.
        val row = IntArray(w)
        for (i in 0 until h) {
            val fromY = sy + i
            val toY = fromY
            if (fromY !in 0 until src.height) continue
            if (toY !in 0 until height) continue
            val fromX = sx
            for (j in 0 until w) {
                val fx = fromX + j
                row[j] = if (fx in 0 until src.width) src.pixels[fromY * src.width + fx] else 0
            }
            System.arraycopy(row, 0, pixels, toY * width + sx.coerceAtLeast(0), w)
        }
    }
}
