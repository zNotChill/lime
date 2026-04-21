package me.znotchill.lime.client

import io.ktor.network.sockets.Socket
import me.znotchill.lime.servers.Server
import me.znotchill.lime.utils.SocketAddress

data class ServerConnectionResponse(
    val address: SocketAddress,
    val server: Server,
    val socket: Socket
)
