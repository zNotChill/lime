package me.znotchill.lime.client.connection

import dev.whyoleg.cryptography.DelicateCryptographyApi
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.crypt.CFB8State
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.*
import me.znotchill.lime.registries.PacketProtocolRegistry
import me.znotchill.lime.zlib.ZLib

@OptIn(DelicateCryptographyApi::class)
abstract class Connection(
    val socket: Socket,
    val scope: CoroutineScope
) : Loggable {
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)

    var compressionThreshold: Int = -1
    var protocol: Int = 0

    private var encryptState: CFB8State? = null
    private var decryptState: CFB8State? = null

    fun enableEncryption(keyBytes: ByteArray) {
        encryptState = CFB8State(keyBytes, keyBytes)
        decryptState = CFB8State(keyBytes, keyBytes)
    }


    fun disableEncryption() {
        encryptState = null
        decryptState = null
    }

    private suspend fun readEncryptedByte(): Byte {
        val b = readChannel.readByte()
        return decryptState?.decryptByte(b) ?: b
    }

    private suspend fun readEncryptedVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = readEncryptedByte().toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift >= 35) throw IllegalStateException("VarInt too large")
        }
        return result
    }

    suspend fun sendPacket(
        packet: MinecraftPacket,
        state: ConnectionState,
        direction: PipeDirection,
        protocol: Int
    ) {
        val buffer = Buffer()

        val idName = packet.id.value

        val packetId = PacketProtocolRegistry.getId(
            version = protocol,
            state = state,
            direction = direction,
            name = idName
        ) ?: throw IllegalStateException(
            "Packet $idName not found for version $protocol state $state dir $direction"
        )

        packet.encode(buffer)
        val payload = buffer.readByteArray()

        sendRawPacket(packetId, Buffer().apply { write(payload) })
    }

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

            val outBytes = out.readByteArray()
            val finalBytes = encryptState?.encrypt(outBytes) ?: outBytes
            writeChannel.writeFully(finalBytes)
            writeChannel.flush()
        } catch (e: Exception) {
            if (writeChannel.isClosedForWrite) return
            log.e("Failed to send raw packet: ${e.message}")
        }
    }


    suspend fun readPacket(): RawPacket {
        val packetLength = readEncryptedVarInt()

        val raw = ByteArray(packetLength)
        readChannel.readFully(raw)

        val decrypted = decryptState?.decrypt(raw) ?: raw
        val temp = Buffer().apply { write(decrypted) }

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

        // bruh
//        scope.cancel()
    }
}