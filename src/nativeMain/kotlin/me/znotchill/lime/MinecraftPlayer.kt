package me.znotchill.lime

import io.ktor.network.sockets.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.znotchill.lime.components.Component
import me.znotchill.lime.packets.registry.clientbound.configuation.ConfigDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.login.LoginDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.play.PlayDisconnectPacket
import me.znotchill.lime.packets.registry.clientbound.play.SystemChatPacket
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
    var state: ConnectionState = ConnectionState.HANDSHAKE
) {
    var username: String = ""
    var uuid: UUID = UUID(0L, 0L)
    var protocol: Int = 0
    
    var remoteConnection: ClientConnection? = null

    suspend fun disconnect(reason: Component) {
        // I have no idea why these are 3 different packets
        val packet = when (state) {
            ConnectionState.LOGIN -> LoginDisconnectPacket(reason)
            ConnectionState.CONFIGURATION -> ConfigDisconnectPacket(reason)
            ConnectionState.PLAY -> PlayDisconnectPacket(reason)
            else -> PlayDisconnectPacket(reason)
        }
        clientConnection.sendPacket(packet)
        clientConnection.close()
        remoteConnection?.close()
    }

    suspend fun disconnect(reason: String) {
        disconnect(Component.text(reason))
    }
    
    suspend fun switchServer(newSocket: Socket, scope: CoroutineScope) {
        remoteConnection?.close()
        remoteConnection = ClientConnection(newSocket, scope)
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