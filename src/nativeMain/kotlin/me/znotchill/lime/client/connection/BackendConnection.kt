package me.znotchill.lime.client.connection

import io.ktor.network.sockets.Socket
import kotlinx.coroutines.CoroutineScope
import me.znotchill.lime.client.MinecraftPlayer

class BackendConnection(socket: Socket, scope: CoroutineScope)
    : Connection(socket, scope) {
    lateinit var player: MinecraftPlayer
    override val loggerTag: String
        get() = if (::player.isInitialized) {
            "BackendConn@${player.username}"
        } else {
            "BackendConn@Player"
        }
}