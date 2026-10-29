package dev.paddeck.vnc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

class RfbClientTest {

    @Test
    fun connectsAuthenticatesAndReceivesPixels() {
        val server = FakeServer(4, 2).apply { start() }
        try {
            val ready = CountDownLatch(1)
            val frame = CountDownLatch(1)
            val client = RfbClient("127.0.0.1", server.port, "secret", object : RfbClient.Listener {
                override fun onReady(desktopName: String, width: Int, height: Int) {
                    ready.countDown()
                }

                override fun onResize(width: Int, height: Int) = Unit

                override fun onFrame(dirtyX: Int, dirtyY: Int, dirtyWidth: Int, dirtyHeight: Int) {
                    frame.countDown()
                }

                override fun onClipboard(text: String) = Unit
                override fun onBell() = Unit
                override fun onClosed(reason: String) = Unit
            })
            client.start()

            assertTrue("서버 인사가 오지 않았습니다", ready.await(5, TimeUnit.SECONDS))
            assertTrue("화면 갱신이 오지 않았습니다", frame.await(5, TimeUnit.SECONDS))

            val pixels = IntArray(4 * 2)
            assertTrue(client.copyPixelsInto(pixels))
            assertEquals(0xFFFF0000.toInt(), pixels[0])
            assertEquals(0xFF00FF00.toInt(), pixels[1])
            assertEquals(0xFF0000FF.toInt(), pixels[2])
            assertEquals(0xFF0000FF.toInt(), pixels[6])

            client.sendPointer(1, 3, 1)
            client.sendKey(true, 0x61)
            assertTrue(server.awaitPointer(5, TimeUnit.SECONDS))
            val pointer = server.pointers.last()
            assertEquals(1, pointer[0])
            assertEquals(3, pointer[1])
            assertEquals(1, pointer[2])
            assertEquals(0x61, server.keys.last()[1])
            client.close()
        } finally {
            server.close()
        }
    }

    @Test
    fun decodesSolidZrleTile() {
        val tile = ByteArrayOutputStream()
        DeflaterOutputStream(tile, Deflater()).use { it.write(byteArrayOf(1, 0x10, 0x20, 0x30)) }
        val compressed = tile.toByteArray()

        val message = ByteArrayOutputStream()
        message.write(byteArrayOf(
            0, 0, 0, compressed.size.toByte(),
        ))
        message.write(compressed)

        val input = RfbInput(ByteArrayInputStream(message.toByteArray()))
        val fb = Framebuffer(10, 10)
        ZrleDecoder(PixelFormat.REQUESTED).decode(input, fb, 0, 0, 10, 10)

        assertEquals(0xFF302010.toInt(), fb.pixels[0])
        assertEquals(0xFF302010.toInt(), fb.pixels[99])
    }

    @Test
    fun decodesHextileBackgroundFill() {
        val message = ByteArrayOutputStream()
        message.write(2)
        message.write(byteArrayOf(0x10, 0x20, 0x30, 0x00))

        val input = RfbInput(ByteArrayInputStream(message.toByteArray()))
        val fb = Framebuffer(4, 4)
        HextileDecoder(PixelFormat.REQUESTED).decode(input, fb, 1, 1, 2, 2)

        assertEquals(0xFF302010.toInt(), fb.pixels[1 * 4 + 1])
        assertEquals(0xFF302010.toInt(), fb.pixels[2 * 4 + 2])
        assertEquals(0, fb.pixels[0])
    }
}

private class FakeServer(private val width: Int, private val height: Int) : Closeable {
    private val serverSocket = ServerSocket(0)
    val pointers = CopyOnWriteArrayList<IntArray>()
    val keys = CopyOnWriteArrayList<IntArray>()
    private val pointerLatch = CountDownLatch(1)
    private val thread = Thread { serve() }.apply { isDaemon = true }

    val port: Int get() = serverSocket.localPort

    fun start() = thread.start()

    fun awaitPointer(timeout: Long, unit: TimeUnit): Boolean = pointerLatch.await(timeout, unit)

    private fun serve() {
        try {
            serverSocket.accept().use { socket ->
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

                output.writeBytes("RFB 003.008\n")
                output.flush()
                input.readFully(ByteArray(12))

                output.writeByte(1)
                output.writeByte(SecType.VNC_AUTH)
                output.flush()
                input.readByte()

                output.write(ByteArray(16) { (it + 1).toByte() })
                output.flush()
                input.readFully(ByteArray(16))

                output.writeInt(0)
                output.flush()
                input.readByte()

                output.writeShort(width)
                output.writeShort(height)
                output.write(byteArrayOf(32, 24, 0, 1, 0, -1, 0, -1, 0, -1, 16, 8, 0, 0, 0, 0))
                output.writeInt(4)
                output.writeBytes("pad1")
                output.flush()

                var sentUpdate = false
                while (true) {
                    when (val type = input.readUnsignedByte()) {
                        0 -> input.readFully(ByteArray(19))
                        2 -> {
                            input.readByte()
                            val count = input.readUnsignedShort()
                            input.readFully(ByteArray(count * 4))
                        }

                        3 -> {
                            input.readByte()
                            input.readUnsignedShort()
                            input.readUnsignedShort()
                            input.readUnsignedShort()
                            input.readUnsignedShort()
                            if (!sentUpdate) {
                                sentUpdate = true
                                sendUpdate(output)
                            }
                        }

                        4 -> {
                            input.readByte()
                            input.readShort()
                            keys += intArrayOf(0, input.readInt())
                        }

                        5 -> {
                            val mask = input.readUnsignedByte()
                            val x = input.readUnsignedShort()
                            val y = input.readUnsignedShort()
                            pointers += intArrayOf(mask, x, y)
                            pointerLatch.countDown()
                        }

                        else -> return
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun sendUpdate(output: DataOutputStream) {
        output.writeByte(0)
        output.writeByte(0)
        output.writeShort(2)

        output.writeShort(0)
        output.writeShort(0)
        output.writeShort(2)
        output.writeShort(1)
        output.writeInt(Enc.RAW)
        writePixel(output, 0xFF0000)
        writePixel(output, 0x00FF00)

        output.writeShort(2)
        output.writeShort(0)
        output.writeShort(2)
        output.writeShort(2)
        output.writeInt(Enc.HEXTILE)
        output.writeByte(2)
        writePixel(output, 0x0000FF)
        output.flush()
    }

    private fun writePixel(output: DataOutputStream, rgb: Int) {
        output.writeByte(rgb and 0xFF)
        output.writeByte((rgb shr 8) and 0xFF)
        output.writeByte((rgb shr 16) and 0xFF)
        output.writeByte(0)
    }

    override fun close() {
        try {
            serverSocket.close()
        } catch (_: Throwable) {
        }
    }
}
