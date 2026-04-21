package me.znotchill.lime.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.znotchill.lime.client.connection.BackendConnection
import me.znotchill.lime.client.connection.ClientConnection
import me.znotchill.lime.client.pipeline.SessionPipeline
import me.znotchill.lime.client.pipeline.SessionSwitcher
import me.znotchill.lime.components.Component
import me.znotchill.lime.generated.Identifiable
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.RawPacket
import me.znotchill.lime.packets.registry.clientbound.configuation.ConfigDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.login.LoginDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.play.PlayDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.play.SystemChatPacket
import me.znotchill.lime.registries.PacketProtocolRegistry
import me.znotchill.lime.servers.Server
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
    var backendState: ConnectionState = ConnectionState.HANDSHAKE
    var username: String = ""
    override val loggerTag: String
        get() = "Player@$username"

    var uuid: UUID = UUID(0L, 0L)
    var protocol: Int = 0

    var remoteConnection: BackendConnection? = null
    lateinit var pipeline: SessionPipeline

    lateinit var handshakePacket: RawPacket
    lateinit var currentServer: Server

    val switcher = SessionSwitcher(this)

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

    fun getPacketName(id: Int, stateOverride: ConnectionState? = null, direction: PipeDirection = PipeDirection.CLIENT): String {
        val targetState = stateOverride ?: this.state

        return PacketProtocolRegistry.getName(
            version = this.protocol,
            state = targetState,
            direction = direction,
            id = id
        ) ?: throw IllegalStateException(
            "Packet name for $id not found for version $protocol in state $targetState"
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

    suspend fun send(component: Component) {
        clientConnection.sendPacket(SystemChatPacket(component, false))
    }

    suspend fun send(text: String) {
        clientConnection.sendPacket(SystemChatPacket(text, false))
    }

    fun queue(block: suspend () -> Unit = {}): Job {
        return clientConnection.scope.launch {
            block()
        }
    }
}