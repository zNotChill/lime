package me.znotchill.lime.client.pipeline

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import me.znotchill.lime.LimeProxy
import me.znotchill.lime.ProxyPhase
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.connection.BackendConnection
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.exceptions.InvalidServerException
import me.znotchill.lime.exceptions.NoConnectionException
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.registry.clientbound.play.PlayDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.play.StartConfigurationPacket
import me.znotchill.lime.packets.registry.serverbound.handshake.HandshakePacket
import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.servers.Server
import me.znotchill.lime.servers.ServerManager

class SessionSwitcher(
    val player: MinecraftPlayer
) : Loggable {
    override val loggerTag = "Switcher"

    var pendingServerSwitch: Server? = null
    var newServerSwitchSocket: Socket? = null

    suspend fun initBackendConnection(
        server: Server,
        selector: SelectorManager = LimeProxy.selectorManager
    ): Socket? {
        log.i("Connecting to ${server.name}...")

        return try {
            withTimeout(ConfigManager.server.socketTimeout) {
                aSocket(selector).tcp().connect(server.address.host, server.address.port)
            }
        } catch (_: IOException) {
            throw NoConnectionException("Server is unreachable")
        } catch (e: Exception) {
            throw e
        }
    }

    fun switchServer(
        serverName: String,
        selector: SelectorManager = LimeProxy.selectorManager,
        onError: suspend (Exception) -> Unit = {}
    ) {
        val server = ServerManager.get(serverName) ?: run {
            player.scope.launch { onError(InvalidServerException("Server does not exist")) }
            return
        }

        player.scope.launch {
            val socket = try {
                initBackendConnection(server, selector)
            } catch (e: Exception) {
                onError(NoConnectionException(e.message ?: "Failed to connect"))
                return@launch
            }

            val startConfig = StartConfigurationPacket()
            player.clientConnection.sendPacket(startConfig)
            pendingServerSwitch = server
            newServerSwitchSocket = socket
        }
    }

    fun handleConfigSwitch(selector: SelectorManager) {
        if (newServerSwitchSocket == null)
            throw IllegalStateException("New server socket is null")
        if (pendingServerSwitch == null)
            throw IllegalStateException("No server switch pending")
        val server = pendingServerSwitch!!

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            performBackendSwitch(
                server,
                selector,
                newServerSwitchSocket!!
            )
        }
    }

    suspend fun performBackendSwitch(
        server: Server,
        selector: SelectorManager,
        serverSocket: Socket
    ) {
        player.pipeline.phase = ProxyPhase.SWITCHING
        player.pipeline.droppingBackendPackets = true
        try {
            val backend = BackendConnection(serverSocket, player.scope)
            backend.compressionThreshold = -1
            backend.protocol = player.protocol

            val handshake = HandshakePacket(
                protocolVersion = player.protocol,
                serverAddress = server.address.host,
                serverPort = server.address.port,
                nextState = 2
            )

            backend.sendPacket(handshake, ConnectionState.HANDSHAKE)

            val loginStart = LoginStartPacket(
                name = player.username,
                uuid = player.uuid
            )

            backend.sendPacket(loginStart, ConnectionState.LOGIN)
            val oldBackend = player.pipeline.backend
            if (oldBackend != null) {
                try {
                    val disconnect = PlayDisconnectPacket("Transferred to another server")
                    oldBackend.sendPacket(disconnect, ConnectionState.PLAY)
                } catch (_: Exception) {}
            }

            player.pipeline.backendReaderScope?.cancel()
            player.pipeline.backend?.socket?.close()
            player.remoteConnection?.close()

            player.pipeline.backend = backend
            player.remoteConnection = backend
            player.backendState = ConnectionState.LOGIN

            player.pipeline.startBackendReader(player)
            log.i("Backend switch completed to ${server.name}")
        } catch (e: Exception) {
            log.e("Backend switch failed: ${e.message}")
            serverSocket.close()
            player.disconnect("Server switch failed")
        }
    }

    fun completeSwitch() {
        log.i("Completing switch")

        // allow packets again
        player.pipeline.phase = ProxyPhase.NORMAL

        // sync states
        player.state = ConnectionState.PLAY

        val backend = player.remoteConnection
        val client = player.clientConnection
        player.currentServer = player.switcher.pendingServerSwitch!!
        player.switcher.pendingServerSwitch = null
        player.switcher.newServerSwitchSocket = null

        if (backend != null) {
            client.compressionThreshold = backend.compressionThreshold
        }

        log.i("Switch complete")
    }
}