package me.znotchill.lime.client.connection

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.readByteArray
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.client.ServerConnectionResponse
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.events.PacketEventManager
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.MinecraftPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readVarInt
import me.znotchill.lime.packets.registry.serverbound.handshake.HandshakePacket
import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.registries.PacketProtocolRegistry
import me.znotchill.lime.utils.toSocketAddress

class ClientConnection(
    socket: Socket,
    parentScope: CoroutineScope,
) : Connection(socket, parentScope) {
    lateinit var player: MinecraftPlayer
    override var loggerTag = "Conn@Player"
    var bridgeJob: Job? = null

    suspend fun sendPacket(packet: MinecraftPacket, direction: PipeDirection) {
        val state = (packet as? ClientPacket)?.state ?: ConnectionState.STATUS

        sendPacket(
            packet = packet,
            state = state,
            direction = direction,
            protocol = this.protocol
        )
    }

    suspend fun sendPacket(packet: MinecraftPacket) {
        val state = (packet as? ClientPacket)?.state ?: ConnectionState.STATUS

        sendPacket(
            packet = packet,
            state = state,
            direction = PipeDirection.CLIENT,
            protocol = this.protocol
        )
    }

    suspend fun tryAllServers(selector: SelectorManager): ServerConnectionResponse? {
        val tryList = ConfigManager.server.servers.tryList
        if (tryList.isEmpty()) {
            player.disconnect("No configured try ist!")
            return null
        }

        tryList.forEach { serverName ->
            val server = ConfigManager.server.servers.servers[serverName]
            if (server == null) {
                log.e("Misconfigured server \"$serverName\"! Defined in try list, but the server config is absent!")
                player.disconnect("Misconfigured server! Check logs for more info!")
                return null
            }

            val address = server.toSocketAddress()

            val serverSocket = try {
                log.i("Connecting to $address")
                withTimeout(ConfigManager.server.socketTimeout) {
                    aSocket(selector).tcp().connect(address.host, address.port)
                }
            } catch (e: Exception) {
                log.e("Cannot connect to ${address.host}:${address.port}: ${e.message}")
                null
            }

            if (serverSocket != null) {
                return ServerConnectionResponse(
                    address,
                    serverSocket
                )
            }
        }

        return null
    }

    suspend fun handlePlayerSession(player: MinecraftPlayer, selector: SelectorManager) {
        try {
            val rawHandshake = player.clientConnection.readPacket()
            player.handshakePacket = rawHandshake

            val handshake = HandshakePacket.decode(rawHandshake.data)

            player.protocol = handshake.protocolVersion
            player.clientConnection.protocol = handshake.protocolVersion

            if (handshake.nextState == 1) {
                player.clientConnection.handleStatus(player)
            } else if (handshake.nextState == 2) {
                player.state = ConnectionState.LOGIN

                val rawLoginStart = player.clientConnection.readPacket()
                player.loginStartPacket = rawLoginStart

                val loginStart = LoginStartPacket.decode(rawLoginStart.data.peek())
                player.username = loginStart.name

                loggerTag = "Conn@${player.username}"

                val server = tryAllServers(selector)

                if (server == null) {
                    player.disconnect("No server found to connect you to!")
                    return
                }

                val backend = BackendConnection(server.socket, player.clientConnection.scope)
                backend.player = player

                player.remoteConnection = backend

                backend.sendRawPacket(rawHandshake.id, rawHandshake.data)
                backend.sendRawPacket(rawLoginStart.id, rawLoginStart.data)

                startBridge(player)
            }
        } catch (e: Exception) {
            player.disconnect("Handshake failed for ${player.username}: ${e.message}")
            return
        }
    }

    suspend fun startBridge(player: MinecraftPlayer) {
        val client = player.clientConnection
        val server = player.remoteConnection ?: return

        bridgeJob = scope.launch {
            // serverbound
            launch { handlePipe(player, client, server, PipeDirection.SERVER) }
            // clientbound
            launch { handlePipe(player, server, client, PipeDirection.CLIENT) }
        }
    }

    private suspend fun handlePipe(
        player: MinecraftPlayer,
        from: Connection,
        to: Connection,
        direction: PipeDirection
    ) {
        try {
            while (from.scope.isActive) {
                if (to.socket.isClosed) return
                val raw = from.readPacket()

                // Compression (serverbound 0x03 during LOGIN)
                if (raw.id == 0x03 && player.state == ConnectionState.LOGIN) {
                    val threshold = raw.data.peek().readVarInt()
                    to.sendRawPacket(raw.id, raw.data)
                    log.i("Setting compression threshold to $threshold")
                    from.compressionThreshold = threshold
                    to.compressionThreshold = threshold
                    continue
                }

                // LOGIN -> CONFIGURATION (clientbound Login Success)
                if (direction == PipeDirection.CLIENT
                    && player.state == ConnectionState.LOGIN
                    && raw.id == 0x02
                ) {
                    player.state = ConnectionState.CONFIGURATION
                }

                // CONFIGURATION -> PLAY (clientbound Finish Configuration)
                if (direction == PipeDirection.CLIENT
                    && player.state == ConnectionState.CONFIGURATION
                    && raw.id == 0x03
                ) {
                    player.state = ConnectionState.PLAY
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
        } catch (e: CancellationException) {
            log.i("Bridge ${direction.name} cancelled")
        } catch (e: Exception) {
            player.disconnect("Bridge error from ${direction.name}: ${e.message}")
        }
    }

    suspend fun handleStatus(player: MinecraftPlayer) {
        while (player.clientConnection.scope.isActive) {
            val raw = player.clientConnection.readPacket()

            when (raw.id) {
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
}