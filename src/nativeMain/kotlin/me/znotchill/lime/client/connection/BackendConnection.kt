package me.znotchill.lime.client.connection

import io.ktor.network.sockets.Socket
import kotlinx.coroutines.CoroutineScope
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.packets.MinecraftPacket

class BackendConnection(socket: Socket, scope: CoroutineScope)
    : Connection(socket, scope) {
    lateinit var player: MinecraftPlayer
    override val loggerTag: String
        get() = if (::player.isInitialized) {
            "BackendConn@${player.username}"
        } else {
            "BackendConn@Player"
        }

    suspend fun sendPacket(packet: MinecraftPacket, state: ConnectionState) {
        sendPacket(
            packet = packet,
            state = state,
            direction = PipeDirection.SERVER,
            protocol = this.protocol
        )
    }
}