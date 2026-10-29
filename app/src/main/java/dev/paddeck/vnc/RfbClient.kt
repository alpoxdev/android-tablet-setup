package dev.paddeck.vnc

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 최소한의 RFB 3.8 클라이언트입니다.
 * 화면 갱신은 [fb]에 담기고, 입력은 [sendPointer]·[sendKey]로 보냅니다.
 */
class RfbClient(
    private val host: String,
    private val port: Int,
    private val password: String,
    private val listener: Listener,
) : Closeable {

    interface Listener {
        fun onReady(desktopName: String, width: Int, height: Int)
        fun onResize(width: Int, height: Int)
        fun onFrame(dirtyX: Int, dirtyY: Int, dirtyWidth: Int, dirtyHeight: Int)
        fun onClipboard(text: String)
        fun onBell()
        fun onClosed(reason: String)
    }

    private val fbLock = Any()
    private var buffer: Framebuffer? = null
    private var socket: Socket? = null
    private var input: RfbInput? = null
    private var output: RfbOutput? = null
    private val sendLock = Any()
    private var thread: Thread? = null

    @Volatile
    private var running = false

    val width: Int get() = buffer?.width ?: 0
    val height: Int get() = buffer?.height ?: 0

    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "rfb-$host:$port").apply {
            isDaemon = true
            start()
        }
    }

    /** 화면 내용을 [out] 버퍼로 복사합니다. UI 스레드에서 호출합니다. */
    fun copyPixelsInto(out: IntArray): Boolean {
        synchronized(fbLock) {
            val fb = buffer ?: return false
            if (out.size != fb.pixels.size) return false
            System.arraycopy(fb.pixels, 0, out, 0, fb.pixels.size)
            return true
        }
    }

    fun sendPointer(buttonMask: Int, x: Int, y: Int) {
        val out = output ?: return
        try {
            synchronized(sendLock) {
                out.u8(5)
                out.u8(buttonMask)
                out.u16(x.coerceIn(0, maxOf(0, width - 1)))
                out.u16(y.coerceIn(0, maxOf(0, height - 1)))
                out.flush()
            }
        } catch (t: IOException) {
            closeWith("포인터 전송 실패: ${t.message}")
        }
    }

    fun sendKey(down: Boolean, keysym: Int) {
        val out = output ?: return
        try {
            synchronized(sendLock) {
                out.u8(4)
                out.u8(if (down) 1 else 0)
                out.u16(0)
                out.u32(keysym)
                out.flush()
            }
        } catch (t: IOException) {
            closeWith("키 전송 실패: ${t.message}")
        }
    }

    fun sendClipboard(text: String) {
        val out = output ?: return
        try {
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            synchronized(sendLock) {
                out.u8(6)
                out.u8(0)
                out.u8(0)
                out.u8(0)
                out.u32(bytes.size)
                out.bytes(bytes)
                out.flush()
            }
        } catch (t: IOException) {
            closeWith("클립보드 전송 실패: ${t.message}")
        }
    }

    private fun runLoop() {
        try {
            handshake()
            listener.onReady(handshakeName, buffer?.width ?: 0, buffer?.height ?: 0)
            readMessages()
        } catch (t: Throwable) {
            if (running) closeWith(describe(t))
        }
    }

    private var handshakeName = ""

    private fun handshake() {
        val sock = Socket()
        socket = sock
        sock.tcpNoDelay = true
        sock.connect(InetSocketAddress(host, port), 6000)
        sock.soTimeout = 15000

        val rawIn = BufferedInputStream(sock.getInputStream(), 128 * 1024)
        val rawOut = BufferedOutputStream(sock.getOutputStream(), 32 * 1024)
        val rfbIn = RfbInput(rawIn)
        val rfbOut = RfbOutput(rawOut)

        val version = rfbIn.string(12)
        val major = version.substring(4, 7).toIntOrNull() ?: 0
        if (major != 3) throw IOException("지원하지 않는 RFB 버전입니다: $version")
        val minor = version.substring(8, 11).toIntOrNull() ?: 8
        rfbOut.bytes("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
        rfbOut.flush()

        val securityTypes = if (minor >= 7) {
            val count = rfbIn.u8()
            if (count == 0) throw IOException("서버가 보안 방식을 거부했습니다: ${rfbIn.string(rfbIn.i32())}")
            IntArray(count) { rfbIn.u8() }
        } else {
            intArrayOf(rfbIn.i32())
        }

        val chosen = when {
            password.isNotEmpty() && SecType.VNC_AUTH in securityTypes -> SecType.VNC_AUTH
            SecType.NONE in securityTypes -> SecType.NONE
            SecType.VNC_AUTH in securityTypes -> SecType.VNC_AUTH
            else -> throw IOException("쓸 수 있는 보안 방식이 없습니다: ${securityTypes.joinToString()}")
        }
        rfbOut.u8(chosen)
        rfbOut.flush()

        if (chosen == SecType.VNC_AUTH) {
            val challenge = rfbIn.bytes(16)
            val key = Des.vncKey(password)
            rfbOut.bytes(Des.encrypt(key, challenge.copyOfRange(0, 8)))
            rfbOut.bytes(Des.encrypt(key, challenge.copyOfRange(8, 16)))
            rfbOut.flush()
        }

        val result = rfbIn.u32()
        if (result != 0L) {
            val reason = if (minor >= 8) {
                val length = rfbIn.u32().toInt()
                if (length > 0) rfbIn.string(minOf(length, 4096)) else ""
            } else {
                ""
            }
            throw IOException(if (reason.isBlank()) "인증에 실패했습니다" else "인증 실패: $reason")
        }

        rfbOut.u8(1)
        rfbOut.flush()

        val width = rfbIn.u16()
        val height = rfbIn.u16()
        readPixelFormat(rfbIn)
        val nameLength = rfbIn.i32()
        handshakeName = if (nameLength > 0) rfbIn.string(minOf(nameLength, 4096)) else ""

        synchronized(fbLock) { buffer = Framebuffer(width, height) }

        writePixelFormat(rfbOut, PixelFormat.REQUESTED)
        writeEncodings(rfbOut)
        requestUpdate(rfbOut, incremental = false, width = width, height = height)
        rfbOut.flush()

        input = rfbIn
        output = rfbOut
        sock.soTimeout = 0
    }

    private fun readPixelFormat(input: RfbInput) {
        input.u8()
        input.u8()
        input.u8()
        input.u8()
        input.u16()
        input.u16()
        input.u16()
        input.u8()
        input.u8()
        input.u8()
        input.skip(3)
    }

    private fun writePixelFormat(out: RfbOutput, pf: PixelFormat) {
        out.u8(0)
        out.u8(0)
        out.u8(0)
        out.u8(0)
        out.u8(pf.bitsPerPixel)
        out.u8(pf.depth)
        out.u8(if (pf.bigEndian) 1 else 0)
        out.u8(if (pf.trueColor) 1 else 0)
        out.u16(pf.redMax)
        out.u16(pf.greenMax)
        out.u16(pf.blueMax)
        out.u8(pf.redShift)
        out.u8(pf.greenShift)
        out.u8(pf.blueShift)
        out.u8(0)
        out.u8(0)
        out.u8(0)
    }

    private fun writeEncodings(out: RfbOutput) {
        val encodings = intArrayOf(Enc.ZRLE, Enc.HEXTILE, Enc.COPY_RECT, Enc.RAW, Enc.DESKTOP_SIZE)
        out.u8(2)
        out.u8(0)
        out.u16(encodings.size)
        encodings.forEach { out.u32(it) }
    }

    private fun requestUpdate(out: RfbOutput, incremental: Boolean, width: Int, height: Int) {
        out.u8(3)
        out.u8(if (incremental) 1 else 0)
        out.u16(0)
        out.u16(0)
        out.u16(width)
        out.u16(height)
        out.flush()
    }

    private fun readMessages() {
        val input = input ?: throw IOException("연결이 준비되지 않았습니다")
        val out = output ?: throw IOException("연결이 준비되지 않았습니다")
        val hextile = HextileDecoder(PixelFormat.REQUESTED)
        val zrle = ZrleDecoder(PixelFormat.REQUESTED)

        while (running) {
            when (val type = input.u8()) {
                0 -> {
                    input.u8()
                    val count = input.u16()
                    var minX = Int.MAX_VALUE
                    var minY = Int.MAX_VALUE
                    var maxX = -1
                    var maxY = -1
                    var resized = false

                    repeat(count) {
                        val x = input.u16()
                        val y = input.u16()
                        val w = input.u16()
                        val h = input.u16()
                        val encoding = input.i32()

                        when (encoding) {
                            Enc.RAW -> synchronized(fbLock) {
                                val fb = buffer ?: return@synchronized
                                for (row in 0 until h) {
                                    for (col in 0 until w) {
                                        fb.set(x + col, y + row, readPixel(input, PixelFormat.REQUESTED))
                                    }
                                }
                            }

                            Enc.HEXTILE -> synchronized(fbLock) {
                                buffer?.let { hextile.decode(input, it, x, y, w, h) }
                            }

                            Enc.ZRLE -> synchronized(fbLock) {
                                buffer?.let { zrle.decode(input, it, x, y, w, h) }
                            }

                            Enc.COPY_RECT -> {
                                val srcX = input.u16()
                                val srcY = input.u16()
                                synchronized(fbLock) {
                                    buffer?.copyFrom(buffer ?: return@synchronized, srcX, srcY, w, h)
                                }
                            }

                            Enc.DESKTOP_SIZE -> {
                                synchronized(fbLock) { buffer?.resize(w, h) }
                                resized = true
                            }

                            else -> throw IOException("지원하지 않는 인코딩입니다: $encoding")
                        }

                        if (encoding != Enc.DESKTOP_SIZE) {
                            minX = minOf(minX, x)
                            minY = minOf(minY, y)
                            maxX = maxOf(maxX, x + w)
                            maxY = maxOf(maxY, y + h)
                        }
                    }

                    if (resized) {
                        val w = width
                        val h = height
                        listener.onResize(w, h)
                        requestUpdate(out, incremental = false, width = w, height = h)
                    } else {
                        if (maxX > minX && maxY > minY) {
                            listener.onFrame(minX, minY, maxX - minX, maxY - minY)
                        }
                        requestUpdate(out, incremental = true, width = width, height = height)
                    }
                }

                1 -> {
                    input.u8()
                    val first = input.u16()
                    val count = input.u16()
                    input.skip(count * 6)
                    if (first < 0) throw IOException("색상표 응답이 잘못됐습니다")
                }

                2 -> listener.onBell()

                3 -> {
                    input.skip(3)
                    val length = input.u32().toInt()
                    val text = if (length > 0) input.string(minOf(length, 1 shl 20)) else ""
                    listener.onClipboard(text)
                }

                else -> throw IOException("알 수 없는 서버 메시지: $type")
            }
        }
    }

    private fun describe(t: Throwable): String = when (t) {
        is EOFException -> "서버가 연결을 닫았습니다"
        is java.net.SocketTimeoutException -> "응답 시간이 초과됐습니다"
        is java.net.ConnectException -> "연결이 거부됐습니다. 주소와 포트, 화면 공유 설정을 확인하세요"
        is java.net.UnknownHostException -> "주소를 찾을 수 없습니다: $host"
        else -> t.message ?: t.javaClass.simpleName
    }

    private fun closeWith(reason: String) {
        if (!running) return
        running = false
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        listener.onClosed(reason)
    }

    override fun close() {
        running = false
        try {
            socket?.close()
        } catch (_: IOException) {
        }
    }
}
