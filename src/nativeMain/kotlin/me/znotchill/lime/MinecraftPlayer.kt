package me.znotchill.lime

import io.ktor.network.sockets.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.znotchill.lime.components.Component
import me.znotchill.lime.packets.registry.clientbound.play.SystemChatPacket
import me.znotchill.lime.utils.MinecraftUUID

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
    var uuid: MinecraftUUID = MinecraftUUID(0L, 0L)
    var protocol: Int = 0
    
    var remoteConnection: ClientConnection? = null

    suspend fun disconnect(reason: String) {
        println("disconnecting $username: $reason")
        clientConnection.close()
        remoteConnection?.close()
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