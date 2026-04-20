package me.znotchill.lime

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import me.znotchill.lime.events.PacketEventManager
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.*
import me.znotchill.lime.packets.payloads.StatusDescription
import me.znotchill.lime.packets.payloads.StatusPayload
import me.znotchill.lime.packets.payloads.StatusPlayers
import me.znotchill.lime.packets.payloads.StatusVersion
import me.znotchill.lime.packets.registry.serverbound.handshake.HandshakePacket
import me.znotchill.lime.packets.registry.serverbound.status.StatusResponsePacket
import me.znotchill.lime.registries.PacketProtocolRegistry
import me.znotchill.lime.zlib.ZLib

class ClientConnection(
    private val socket: Socket,
    parentScope: CoroutineScope,
) {
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)
    var protocol: Int = 0

    val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    suspend fun sendPacket(packet: MinecraftPacket) {
        val packetBuilder = Buffer()

        val state = (packet as? ClientPacket)?.state ?: ConnectionState.STATUS
        val idName = packet.id.value

        val dynamicId = PacketProtocolRegistry.getId(protocol, state, 0, idName)
            ?: throw IllegalStateException("Packet $idName not found for version $protocol")

        packet.encode(packetBuilder)
        val payload = packetBuilder.readByteArray()

        sendRawPacket(dynamicId, Buffer().apply { write(payload) })
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

                val compressed = ZLib.compress(toCompress.readByteArray())

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
                val decompressed = ZLib.decompress(compressedBytes, dataLength)
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

            player.protocol = handshake.protocolVersion
            player.clientConnection.protocol = handshake.protocolVersion

            if (handshake.nextState == 1) {
                player.clientConnection.handleStatus(player)
            } else if (handshake.nextState == 2) {
                player.state = ConnectionState.LOGIN
                val serverSocket = aSocket(selector).tcp().connect(
                    "127.0.0.1", 25565
                )
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

        coroutineScope {
            // serverbound
            launch { handlePipe(player, client, server, 1) }
            // clientbound
            launch { handlePipe(player, server, client, 0) }
        }
    }

    private suspend fun handlePipe(
        player: MinecraftPlayer,
        from: ClientConnection,
        to: ClientConnection,
        direction: Int
    ) {
        try {
            while (from.scope.isActive) {
                val raw = from.readPacket()

                if (direction == 0 && player.state == ConnectionState.LOGIN && raw.id == 0x02) {
                    player.state = ConnectionState.PLAY
                }

                if (raw.id == 0x03 && player.state != ConnectionState.PLAY) {
                    val threshold = raw.data.peek().readVarInt()

                    to.sendRawPacket(raw.id, raw.data)

                    from.compressionThreshold = threshold
                    to.compressionThreshold = threshold
                    continue
                }

                val decoder = PacketRegistry.get(
                    player.protocol,
                    player.state,
                    direction,
                    raw.id
                )

                if (decoder != null) {
                    try {
                        val decoded = decoder(raw.data.peek())
                        val isCancelled = PacketEventManager.emit(player, decoded)

                        if (isCancelled)
                            continue
                    } catch (e: Exception) {
                    }
                }

                to.sendRawPacket(raw.id, raw.data)
            }
        } catch (e: Exception) {
            player.disconnect("Bridge closed: ${e.message}")
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
                        override val id = Packet.Clientbound.Status.Ping
                            override fun encode(output: Sink) {
                            output.writeLong(time)
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