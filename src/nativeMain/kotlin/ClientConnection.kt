import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.build
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.readPacket
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import packets.MinecraftPacket
import packets.PacketRegistry
import packets.RawPacket
import packets.getVarIntSize
import packets.payloads.StatusDescription
import packets.payloads.StatusPayload
import packets.payloads.StatusPlayers
import packets.payloads.StatusVersion
import packets.readVarInt
import packets.registry.ChatPacket
import packets.registry.HandshakePacket
import packets.registry.StatusResponsePacket
import packets.writeVarInt
import platform.zlib.*
import kotlinx.cinterop.*

class ClientConnection(
    private val socket: Socket,
    parentScope: CoroutineScope
) {
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)

    val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    suspend fun sendPacket(packet: MinecraftPacket) {
        val packetBuilder = Buffer()
        packetBuilder.writeVarInt(packet.id)
        packet.encode(packetBuilder)

        val payload = packetBuilder.build()
        val packetSize = payload.remaining.toInt()

        writeChannel.writeVarInt(packetSize)
        writeChannel.writePacket(payload)
        writeChannel.flush()
    }

    suspend fun sendRawPacket(id: Int, data: Source) {
        val idSize = getVarIntSize(id)
        val payloadBytes = data.readByteArray()
        val uncompressedSize = idSize + payloadBytes.size

        val out = Buffer()

        if (compressionThreshold != -1) {
            if (uncompressedSize >= compressionThreshold) {
                val toCompress = Buffer()
                toCompress.writeVarInt(id)
                toCompress.write(payloadBytes)

                val compressed = compressZlib(toCompress.readByteArray())

                val dataLenSize = getVarIntSize(uncompressedSize)
                out.writeVarInt(dataLenSize + compressed.size)
                out.writeVarInt(uncompressedSize)
                out.write(compressed)
            } else {
                out.writeVarInt(1 + uncompressedSize)
                out.writeVarInt(0)
                out.writeVarInt(id)
                out.write(payloadBytes)
            }
        } else {
            out.writeVarInt(uncompressedSize)
            out.writeVarInt(id)
            out.write(payloadBytes)
        }

        writeChannel.writePacket(out.build())
        writeChannel.flush()
    }

    var compressionThreshold: Int = -1

    suspend fun readPacket(): RawPacket {
        val packetLength = readChannel.readVarInt()
        val rawData = readChannel.readPacket(packetLength)

        if (compressionThreshold != -1) {
            val dataLength = rawData.readVarInt()
            if (dataLength != 0) {
                val compressedBytes = rawData.readByteArray()
                val decompressed = decompressZlib(compressedBytes, dataLength)
                val buffer = Buffer().apply { write(decompressed) }
                return RawPacket(buffer.readVarInt(), buffer)
            }
        }

        return RawPacket(rawData.readVarInt(), rawData)
    }

    suspend fun handlePlayerSession(player: MinecraftPlayer, selector: SelectorManager) {
        try {
            val rawHandshake = player.clientConnection.readPacket()

            val handshake = HandshakePacket.decode(rawHandshake.data.peek())

            println("Player protocol: ${handshake.protocolVersion}, Next State: ${handshake.nextState}")

            if (handshake.nextState == 1) {
                player.clientConnection.handleStatus(player)
            } else if (handshake.nextState == 2) {
                val serverSocket = aSocket(selector).tcp().connect("127.0.0.1", 25565)
                val backend = ClientConnection(serverSocket, player.clientConnection.scope)
                player.remoteConnection = backend

                backend.sendRawPacket(rawHandshake.id, rawHandshake.data)

                startBridge(player)
            }
        } catch (e: Exception) {
            player.disconnect("Handshake failed: ${e.message}")
        }
    }

    suspend fun startBridge(player: MinecraftPlayer) {
        val client = player.clientConnection
        val server = player.remoteConnection ?: return
        var isPlayState = false

        coroutineScope {
            launch {
                try {
                    while (isActive) {
                        val raw = client.readPacket()

                        if (isPlayState) {
                            try {
                                val decoder = PacketRegistry.get(raw.id)
                                if (decoder != null) {
                                    val decoded = decoder(raw.data.peek())
                                    if (decoded is ChatPacket) {
                                        println("Chat: ${decoded.message}")
                                    }
                                }
                            } catch (e: Exception) {
                            }
                        }

                        server.sendRawPacket(raw.id, raw.data)
                    }
                } catch (e: Exception) {
                    player.disconnect("Client side bridge closed: ${e.message}")
                }
            }

            launch {
                try {
                    while (isActive) {
                        val raw = server.readPacket()

                        if (raw.id == 0x03 && !isPlayState) {
                            val threshold = raw.data.peek().readVarInt()
                            println("Setting Compression Threshold to: $threshold")

                            client.sendRawPacket(raw.id, raw.data)

                            client.compressionThreshold = threshold
                            server.compressionThreshold = threshold
                            continue
                        }

                        if (raw.id == 0x02 && !isPlayState) {
                            println("logged in")
                            isPlayState = true
                        }

                        client.sendRawPacket(raw.id, raw.data)
                    }
                } catch (e: Exception) {
                    player.disconnect("Server side bridge closed")
                }
            }
        }
    }

    suspend fun handleStatus(player: MinecraftPlayer) {
        while (player.clientConnection.scope.isActive) {
            val raw = player.clientConnection.readPacket()

            when (raw.id) {
                0x00 -> {
                    val payload = StatusPayload(
                        version = StatusVersion(
                            name = "Hello",
                            protocol = MinecraftVersion.`1_21_11`
                        ),
                        players = StatusPlayers(
                            max = 50000,
                            online = 1000
                        ),
                        description = StatusDescription(
                            text = "AWESOME PROXY!!"
                        )
                    )

                    player.clientConnection.sendPacket(
                        StatusResponsePacket(payload)
                    )
                }
                0x01 -> {
                    val time = raw.data.readLong()
                    val pong = object : MinecraftPacket {
                        override val id = 0x01
                        override fun encode(sink: Sink) {
                            sink.writeLong(time)
                        }
                    }
                    player.clientConnection.sendPacket(pong)
                    return
                }
            }
        }
    }

    fun close() {
        socket.close()
        scope.cancel()
    }
}