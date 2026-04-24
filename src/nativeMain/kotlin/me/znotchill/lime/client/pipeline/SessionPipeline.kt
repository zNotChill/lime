package me.znotchill.lime.client.pipeline

import io.ktor.network.selector.SelectorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import me.znotchill.lime.ProxyPhase
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.client.connection.BackendConnection
import me.znotchill.lime.client.connection.ClientConnection
import me.znotchill.lime.events.packets.PacketEventManager
import me.znotchill.lime.events.proxy.ProxyEventManager
import me.znotchill.lime.events.proxy.registry.PlayerJoinEvent
import me.znotchill.lime.events.proxy.registry.PlayerQuitEvent
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.RawPacket
import me.znotchill.lime.packets.readVarInt
import me.znotchill.lime.packets.registry.serverbound.handshake.HandshakePacket
import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.utils.UUID

class SessionPipeline(
    val client: ClientConnection,
    val scope: CoroutineScope,
    val selector: SelectorManager
) : Loggable {
    lateinit var player: MinecraftPlayer
    override val loggerTag: String
        get() = if (::player.isInitialized) {
            "Pipeline@${player.username}"
        } else {
            "Pipeline@Player"
        }

    val inboundFromClient = Channel<RawPacket>(Channel.UNLIMITED)
    val inboundFromBackend = Channel<RawPacket>(Channel.UNLIMITED)

    var backend: BackendConnection? = null
    var running = true

    var backendReaderScope: CoroutineScope? = null
    var droppingBackendPackets = false
    var phase = ProxyPhase.NORMAL

    fun startClientReader() = scope.launch {
        try {
            while (running && scope.isActive) {
                val packet = client.readPacket()
                if (running) {
                    inboundFromClient.send(packet)
                }
            }
        } catch (_: CancellationException) {
            // intentional, do nothing
        } catch (_: EOFException) {
            // do nothing, since it will error every time a user
            // refreshes the multiplayer menu, or disconnects from the server,
            // or if anything goes wrong where the player stops getting a stream of data
        } catch (e: Exception) {
            if (running) stop("Client reader died: ${e.message}")
        } finally {
            // only send the quit event if the player has made it past the login start
            // stage (where their username and uuid is populated)
            // to avoid triggering the event from server list pings
            if (player.username.isNotEmpty() && player.uuid != UUID(0L, 0L))
                ProxyEventManager.emit(
                    player,
                    PlayerQuitEvent()
                )
        }
    }

    fun startBackendReader(player: MinecraftPlayer) {
        backendReaderScope?.cancel()
        val readerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        backendReaderScope = readerScope

        readerScope.launch {
            try {
                while (running) {
                    val currentBackend = backend ?: break
                    val packet = currentBackend.readPacket()

                    if (currentBackend.compressionThreshold == -1) {
                        val compressionId = player.getPacketId(Packet.Clientbound.Login.Compress, ConnectionState.LOGIN)
                        if (packet.id == compressionId) {
                            val threshold = packet.data.peek().readVarInt()
                            currentBackend.compressionThreshold = threshold
//                            log.d("[BackendReader] Compression threshold applied: $threshold")
                        }
                    }

                    inboundFromBackend.send(packet)
                }
            } catch (_: CancellationException) {
                // intentional, do nothing
            } catch (e: Exception) {
                stop("Backend reader died: ${e.message}")
            }
        }
    }

    fun startDispatcher(player: MinecraftPlayer) = scope.launch {
        try {
            while (running && scope.isActive) {
                try {
                    select<Unit> {
                        inboundFromClient.onReceive { packet ->
                            handleClientPacket(player, packet)
                        }
                        inboundFromBackend.onReceive { packet ->
                            handleBackendPacket(player, packet)
                        }
                    }
                } catch (_: ClosedReceiveChannelException) {
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (running) stop("Dispatcher error: ${e.message}")
        }
    }

    private fun stop(reason: String) {
        if (!running) return
        running = false
        log.w("Pipeline Stopping: $reason")

        scope.launch {
            inboundFromClient.close()
            inboundFromBackend.close()
            client.socket.close()
            backend?.socket?.close()
        }
    }

    private suspend fun processPacket(
        player: MinecraftPlayer,
        packet: RawPacket,
        direction: PipeDirection
    ): Boolean {
        val decoder = PacketRegistry.get(player.protocol, player.state, direction, packet.id)
        if (decoder != null) {
            try {
                val decoded = decoder(packet.data.peek())
                val isCancelled = PacketEventManager.emit(player, decoded)
                if (isCancelled) {
//                    PacketEventManager.log.w("Packet 0x${packet.id.toString(16)} CANCELLED by listener")
                    return true
                }
            } catch (e: Exception) {
            }
        }
        return false
    }

    suspend fun handleClientPacket(player: MinecraftPlayer, packet: RawPacket) {
//        log.d("[C -> S] State: ${player.state} | ID: 0x${packet.id.toString(16)} | Size: ${packet.data.remaining}")

        if (processPacket(player, packet, PipeDirection.SERVER)) return

        when (player.state) {
            ConnectionState.HANDSHAKE -> {
                val handshake = HandshakePacket.decode(packet.data.peek())
                player.protocol = handshake.protocolVersion
                client.protocol = handshake.protocolVersion

                player.state = if (handshake.nextState == 1) ConnectionState.STATUS else ConnectionState.LOGIN
                player.handshakePacket = packet
            }

            ConnectionState.LOGIN -> {
                if (backend == null) {
                    val loginStart = LoginStartPacket.decode(packet.data.peek())
                    player.username = loginStart.name

                    val response = client.tryAllServers(selector) ?: return

                    val newBackend = BackendConnection(response.socket, scope)
                    backend = newBackend
                    player.remoteConnection = newBackend
                    player.currentServer = response.server

                    player.currentServer.addPlayer(player)

                    startBackendReader(player)

                    player.handshakePacket.let {
                        newBackend.sendRawPacket(it.id, it.data)
                    }

                    newBackend.sendRawPacket(packet.id, packet.data.peek())

                    ProxyEventManager.emit(
                        player,
                        PlayerJoinEvent()
                    )
                } else {
                    backend?.sendRawPacket(packet.id, packet.data)
                }
            }

            ConnectionState.CONFIGURATION -> {
                val finishId = player.getPacketId(Packet.Serverbound.Config.FinishConfiguration)

                if (packet.id == finishId) {
                    player.state = ConnectionState.PLAY
                }

                backend?.sendRawPacket(packet.id, packet.data)
            }

            ConnectionState.PLAY -> {
                val ackId = player.getPacketId(Packet.Serverbound.Play.ConfigurationAcknowledged)
                if (packet.id == ackId) {
                    player.state = ConnectionState.CONFIGURATION
                    if (player.switcher.pendingServerSwitch != null) {
                        player.switcher.handleConfigSwitch(selector)
                        return
                    }
                }
                backend?.sendRawPacket(packet.id, packet.data)
            }

            else -> backend?.sendRawPacket(packet.id, packet.data)
        }
    }

    suspend fun handleBackendPacket(player: MinecraftPlayer, packet: RawPacket) {
//        val packetName = try { player.getPacketName(packet.id, player.state, PipeDirection.SERVER) }
//            catch (_: Exception) { "none" }
//        val backendPacketName = try { player.getPacketName(packet.id, player.backendState, PipeDirection.SERVER) }
//            catch (_: Exception) { "none" }
//        log.d("[S -> C] Phase: $phase | State: ${player.state} / $packetName (B: ${player.backendState} / $backendPacketName) | ID: ${packet.id} 0x${packet.id.toString(16)} | Size: ${packet.data.remaining}")
        if (processPacket(player, packet, PipeDirection.CLIENT)) return

        if (phase == ProxyPhase.SWITCHING) {
            when (player.backendState) {
                ConnectionState.LOGIN -> {
                    if (packet.id == player.getPacketId(Packet.Clientbound.Login.Success, ConnectionState.LOGIN)) {
                        player.backendState = ConnectionState.CONFIGURATION

                        val loginAckId = player.getPacketId(Packet.Serverbound.Login.LoginAcknowledged, ConnectionState.LOGIN)
                        backend?.sendRawPacket(loginAckId, Buffer())
                        return
                    }
                }
                ConnectionState.PLAY -> {
                    player.switcher.completeSwitch()
                    return
                }
                ConnectionState.CONFIGURATION -> {
                    val finishId = player.getPacketId(Packet.Clientbound.Config.FinishConfiguration)

                    if (packet.id == finishId) {
                        // NOW tell the client to finish (moving client to PLAY)
                        client.sendRawPacket(packet.id, packet.data)

                        player.switcher.completeSwitch()
                        return
                    }

                    if (player.state == ConnectionState.CONFIGURATION) {
                        client.sendRawPacket(packet.id, packet.data)
                    }
                }
                else -> {
                    return
                }
            }
            return
        }

        val client = player.clientConnection
        when (player.state) {
            ConnectionState.LOGIN -> handleLogin(player, packet)

            ConnectionState.CONFIGURATION -> {
                val finishId = player.getPacketId(Packet.Clientbound.Config.FinishConfiguration)
                if (packet.id == finishId && player.backendState == ConnectionState.CONFIGURATION) {
                    player.state = ConnectionState.PLAY
                }
                client.sendRawPacket(packet.id, packet.data)
            }
            else -> client.sendRawPacket(packet.id, packet.data)
        }
    }

    private suspend fun handleLogin(player: MinecraftPlayer, packet: RawPacket) {
        val client = player.clientConnection
        val backend = player.remoteConnection
        val loginSuccessId = player.getPacketId(Packet.Clientbound.Login.Success, ConnectionState.LOGIN)
        val compressionId = player.getPacketId(Packet.Clientbound.Login.Compress, ConnectionState.LOGIN)

        when (packet.id) {
            compressionId -> {
                val threshold = packet.data.peek().readVarInt()

                client.sendRawPacket(packet.id, packet.data)

                client.compressionThreshold = threshold
                backend?.compressionThreshold = threshold
            }

            loginSuccessId -> {
                client.sendRawPacket(packet.id, packet.data)
                player.state = ConnectionState.CONFIGURATION
            }

            0x00 -> {

            }

            else -> client.sendRawPacket(packet.id, packet.data)
        }
    }
}