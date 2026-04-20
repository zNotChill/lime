package me.znotchill.lime.client.connection

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.RawPacket
import me.znotchill.lime.packets.getVarIntSize
import me.znotchill.lime.packets.readVarInt
import me.znotchill.lime.packets.writeVarInt
import me.znotchill.lime.zlib.ZLib

abstract class Connection(
    val socket: Socket,
    val scope: CoroutineScope
) : Loggable {
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)

    var compressionThreshold: Int = -1
    var protocol: Int = 0

    suspend fun sendRawPacket(id: Int, data: Source, forceUncompressed: Boolean = false) {
        try {
            val payloadBytes = data.readByteArray()
            val out = Buffer()

            if (compressionThreshold != -1 && !forceUncompressed) {
                val uncompressedSize = getVarIntSize(id) + payloadBytes.size

                if (uncompressedSize >= compressionThreshold) {
                    // Compressed
                    val toCompress = Buffer()
                    toCompress.writeVarInt(id)
                    toCompress.write(payloadBytes)

                    val compressed = ZLib.compress(toCompress.readByteArray())

                    val innerBuffer = Buffer()
                    innerBuffer.writeVarInt(uncompressedSize)
                    innerBuffer.write(compressed)

                    out.writeVarInt(innerBuffer.size.toInt())
                    out.write(innerBuffer.readByteArray())
                } else {
                    // Compression enabled, but below the threshold
                    val innerBuffer = Buffer()
                    innerBuffer.writeVarInt(0)
                    innerBuffer.writeVarInt(id)
                    innerBuffer.write(payloadBytes)

                    out.writeVarInt(innerBuffer.size.toInt())
                    out.write(innerBuffer.readByteArray())
                }
            } else {
                // No compression
                val innerBuffer = Buffer()
                innerBuffer.writeVarInt(id)
                innerBuffer.write(payloadBytes)

                out.writeVarInt(innerBuffer.size.toInt())
                out.write(innerBuffer.readByteArray())
            }

            writeChannel.writePacket(out.build())
            writeChannel.flush()
        } catch (e: Exception) {
            if (writeChannel.isClosedForWrite) return
            log.e("Failed to send raw packet: ${e.message}")
        }
    }

    suspend fun readPacket(): RawPacket {
        val packetLength = readChannel.readVarInt()
        val packetData = readChannel.readPacket(packetLength)
        val temp = Buffer().apply { write(packetData.readByteArray()) }

        if (compressionThreshold != -1) {
            val uncompressedLength = temp.readVarInt()

            if (uncompressedLength == 0) {
                val id = temp.readVarInt()
                return RawPacket(id, temp.readByteArray())
            } else {
                val compressedBytes = temp.readByteArray()
                val decompressed = ZLib.decompress(compressedBytes, uncompressedLength)
                val decompressedBuf = Buffer().apply { write(decompressed) }
                val id = decompressedBuf.readVarInt()
                return RawPacket(id, decompressedBuf.readByteArray())
            }
        }

        val id = temp.readVarInt()
        return RawPacket(id, temp.readByteArray())
    }

    fun close() {
        socket.close()
        scope.cancel()
    }
}