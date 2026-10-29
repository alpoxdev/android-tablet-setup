package dev.paddeck.vnc

import java.io.IOException
import java.util.zip.Inflater

internal fun readPixel(input: RfbInput, pf: PixelFormat): Int {
    var raw = 0L
    val size = pf.bitsPerPixel / 8
    for (i in 0 until size) {
        val b = input.u8().toLong()
        raw = if (pf.bigEndian) (raw shl 8) or b else raw or (b shl (8 * i))
    }
    return pf.toArgb(raw.toInt())
}

internal class HextileDecoder(private val pf: PixelFormat) {
    fun decode(input: RfbInput, fb: Framebuffer, rx: Int, ry: Int, rw: Int, rh: Int) {
        var background = 0
        var foreground = 0
        var tileY = 0
        while (tileY < rh) {
            val tileH = minOf(16, rh - tileY)
            var tileX = 0
            while (tileX < rw) {
                val tileW = minOf(16, rw - tileX)
                val sub = input.u8()
                val x0 = rx + tileX
                val y0 = ry + tileY

                if (sub and 1 != 0) {
                    for (y in 0 until tileH) {
                        for (x in 0 until tileW) {
                            fb.set(x0 + x, y0 + y, readPixel(input, pf))
                        }
                    }
                } else {
                    if (sub and 2 != 0) background = readPixel(input, pf)
                    if (sub and 4 != 0) foreground = readPixel(input, pf)
                    if (sub and 8 != 0) {
                        val count = input.u8()
                        repeat(count) {
                            val color = if (sub and 16 != 0) readPixel(input, pf) else foreground
                            val xy = input.u8()
                            val wh = input.u8()
                            val sx = xy shr 4
                            val sy = xy and 0x0F
                            val sw = (wh shr 4) + 1
                            val sh = (wh and 0x0F) + 1
                            fb.fill(x0 + sx, y0 + sy, sw, sh, color)
                        }
                    } else {
                        fb.fill(x0, y0, tileW, tileH, background)
                    }
                }
                tileX += 16
            }
            tileY += 16
        }
    }
}

/**
 * ZRLE 해제용 zlib 스트림.
 * RFB는 연결 하나에 zlib 스트림 하나를 계속 이어 쓰므로,
 * 사각형 단위로 압축 데이터를 넣고 필요한 만큼만 꺼내 씁니다.
 */
internal class ZlibStream {
    private val inflater = Inflater()
    private val out = ByteArray(64 * 1024)
    private var outPos = 0
    private var outLen = 0
    private var finished = false

    fun setInput(data: ByteArray) {
        inflater.setInput(data)
    }

    fun u8(): Int {
        if (outPos >= outLen) refill()
        return out[outPos++].toInt() and 0xFF
    }

    private fun refill() {
        outPos = 0
        outLen = 0
        var guard = 0
        while (outLen == 0) {
            if (inflater.finished()) {
                finished = true
                throw IOException("ZRLE 스트림이 예상보다 일찍 끝났습니다")
            }
            if (inflater.needsInput()) throw IOException("ZRLE 데이터가 부족합니다")
            outLen = inflater.inflate(out)
            if (outLen == 0 && guard++ > 64) throw IOException("ZRLE 압축 해제가 멈췄습니다")
        }
    }

    fun close() {
        if (!finished) inflater.end()
    }
}

internal class ZrleDecoder(private val pf: PixelFormat) {
    private val zlib = ZlibStream()
    private val compact = pf.canUseCompactPixel

    private fun readCPixel(): Int {
        if (!compact) {
            var raw = 0
            val size = pf.bitsPerPixel / 8
            for (i in 0 until size) {
                val b = zlib.u8()
                raw = if (pf.bigEndian) (raw shl 8) or b else raw or (b shl (8 * i))
            }
            return pf.toArgb(raw)
        }
        val b0 = zlib.u8()
        val b1 = zlib.u8()
        val b2 = zlib.u8()
        val raw = if (pf.bigEndian) (b0 shl 16) or (b1 shl 8) or b2
        else (b2 shl 16) or (b1 shl 8) or b0
        return pf.toArgb(raw)
    }

    fun decode(input: RfbInput, fb: Framebuffer, rx: Int, ry: Int, rw: Int, rh: Int) {
        val length = input.i32()
        if (length <= 0) return
        zlib.setInput(input.bytes(length))

        var tileY = 0
        while (tileY < rh) {
            val tileH = minOf(64, rh - tileY)
            var tileX = 0
            while (tileX < rw) {
                val tileW = minOf(64, rw - tileX)
                decodeTile(fb, rx + tileX, ry + tileY, tileW, tileH)
                tileX += 64
            }
            tileY += 64
        }
    }

    fun close() = zlib.close()

    private fun decodeTile(fb: Framebuffer, x0: Int, y0: Int, w: Int, h: Int) {
        val sub = zlib.u8()
        when {
            sub == 0 -> {
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        fb.set(x0 + x, y0 + y, readCPixel())
                    }
                }
            }

            sub == 1 -> {
                val color = readCPixel()
                fb.fill(x0, y0, w, h, color)
            }

            sub in 2..16 -> decodePaletteTile(fb, x0, y0, w, h, sub, rle = false)
            sub == 128 -> decodePlainRleTile(fb, x0, y0, w, h)
            sub in 129..255 -> decodePaletteTile(fb, x0, y0, w, h, sub - 128, rle = true)
            else -> throw IOException("알 수 없는 ZRLE 서브인코딩: $sub")
        }
    }

    private fun decodePaletteTile(
        fb: Framebuffer,
        x0: Int,
        y0: Int,
        w: Int,
        h: Int,
        size: Int,
        rle: Boolean,
    ) {
        val palette = IntArray(size) { readCPixel() }
        val bitsPerIndex = when {
            size <= 2 -> 1
            size <= 4 -> 2
            else -> 4
        }
        val total = w * h

        if (!rle) {
            val rowBytes = (w * bitsPerIndex + 7) / 8
            for (y in 0 until h) {
                val line = ByteArray(rowBytes) { zlib.u8().toByte() }
                for (x in 0 until w) {
                    val bitPos = x * bitsPerIndex
                    val byte = line[bitPos / 8].toInt() and 0xFF
                    val shift = 8 - bitsPerIndex - (bitPos % 8)
                    val idx = (byte shr shift) and ((1 shl bitsPerIndex) - 1)
                    fb.set(x0 + x, y0 + y, palette[idx])
                }
            }
            return
        }

        var written = 0
        while (written < total) {
            var idx = zlib.u8()
            var runLength = 1
            if (idx and 0x80 != 0) {
                idx = idx and 0x7F
                runLength = 1
                while (true) {
                    val b = zlib.u8()
                    runLength += b
                    if (b != 255) break
                }
            }
            val color = palette[idx]
            var i = 0
            while (i < runLength && written < total) {
                fb.set(x0 + written % w, y0 + written / w, color)
                written++
                i++
            }
        }
    }

    private fun decodePlainRleTile(fb: Framebuffer, x0: Int, y0: Int, w: Int, h: Int) {
        val total = w * h
        var written = 0
        while (written < total) {
            val color = readCPixel()
            var runLength = 1
            while (true) {
                val b = zlib.u8()
                runLength += b
                if (b != 255) break
            }
            var i = 0
            while (i < runLength && written < total) {
                fb.set(x0 + written % w, y0 + written / w, color)
                written++
                i++
            }
        }
    }
}
