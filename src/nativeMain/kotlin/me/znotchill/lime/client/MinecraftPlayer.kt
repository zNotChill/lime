package me.znotchill.lime.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import me.znotchill.lime.LimeProxy
import me.znotchill.lime.client.connection.BackendConnection
import me.znotchill.lime.client.connection.ClientConnection
import me.znotchill.lime.client.pipeline.SessionPipeline
import me.znotchill.lime.components.Component
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.generated.Identifiable
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.RawPacket
import me.znotchill.lime.packets.registry.clientbound.configuation.ConfigDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.login.LoginDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.play.PlayDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.play.StartConfigurationPacket
import me.znotchill.lime.packets.registry.clientbound.play.SystemChatPacket
import me.znotchill.lime.registries.PacketProtocolRegistry
import me.znotchill.lime.servers.ServerManager
import me.znotchill.lime.utils.UUID

enum class ConnectionState {
    HANDSHAKE,
    STATUS,
    LOGIN,
    PLAY,
    CONFIGURATION
}

class MinecraftPlayer(
    val clientConnection: ClientConnection,
    val scope: CoroutineScope,
    var state: ConnectionState = ConnectionState.HANDSHAKE
) : Loggable {
    var username: String = ""
    override val loggerTag: String
        get() = "Player@$username"
    var uuid: UUID = UUID(0L, 0L)

    var protocol: Int = 0
    var remoteConnection: BackendConnection? = null

    lateinit var pipeline: SessionPipeline
    var pendingServerSwitch: String? = null

    lateinit var handshakePacket: RawPacket
    lateinit var loginStartPacket: RawPacket

    fun getPacketId(packet: Identifiable, stateOverride: ConnectionState? = null): Int {
        val targetState = stateOverride ?: this.state

        return PacketProtocolRegistry.getId(
            version = this.protocol,
            state = targetState,
            direction = packet.direction,
            name = packet.value
        ) ?: throw IllegalStateException(
            "Packet ${packet.value} not found for version $protocol in state $targetState"
        )
    }

    suspend fun disconnect(reason: Component) {
        val packet = when (state) {
            ConnectionState.LOGIN -> LoginDisconnectPacket(reason)
            ConnectionState.CONFIGURATION -> ConfigDisconnectPacket(reason)
            ConnectionState.PLAY -> PlayDisconnectPacket(reason)
            else -> return
        }

        log.i("Disconnected (${state.name}): \"${reason.toPlainText()}\"")
        clientConnection.sendPacket(packet)
        clientConnection.close()
        remoteConnection?.close()
    }

    suspend fun disconnect(reason: String) {
        disconnect(Component.text(reason))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun switchServer(serverName: String, selector: SelectorManager = LimeProxy.selectorManager): Boolean {
        val startConfig = StartConfigurationPacket()
        clientConnection.sendPacket(startConfig)

        pendingServerSwitch = serverName
        return true
    }

    suspend fun performBackendSwitch(serverName: String, selector: SelectorManager) {
        val server = ServerManager.get(serverName) ?: run {
            log.e("Server $serverName not found during switch")
            disconnect("Server not found: $serverName")
            return
        }

        log.i("Connecting to $serverName...")
        val serverSocket = try {
            withTimeout(ConfigManager.server.socketTimeout) {
                aSocket(selector).tcp().connect(server.address.host, server.address.port)
            }
        } catch (e: Exception) {
            log.e("Connect failed: ${e.message}")
            disconnect("Failed to connect to $serverName")
            return
        }

        try {
            pipeline.backendReaderScope?.cancel()
            pipeline.backend?.socket?.close()
            remoteConnection?.close()

            val backend = BackendConnection(serverSocket, scope)
            pipeline.backend = backend
            remoteConnection = backend

            state = ConnectionState.LOGIN
//            clientConnection.compressionThreshold = -1
            backend.compressionThreshold = -1

            pipeline.startBackendReader(this@MinecraftPlayer)
            pipeline.droppingBackendPackets = false

            backend.sendRawPacket(handshakePacket.id, handshakePacket.data)
            backend.sendRawPacket(loginStartPacket.id, loginStartPacket.data)

            log.i("Backend switch completed to $serverName")
        } catch (e: Exception) {
            log.e("Backend switch failed: ${e.message}")
            serverSocket.close()
            disconnect("Server switch failed")
        }
    }

    fun send(component: Component) {
        val packet = SystemChatPacket(component, false)
        clientConnection.scope.launch {
            clientConnection.sendPacket(packet)
        }
    }
    fun send(text: String) {
        val packet = SystemChatPacket(text, false)
        clientConnection.scope.launch {
            clientConnection.sendPacket(packet)
        }
    }
}