package me.znotchill.lime.client

import io.ktor.network.sockets.*
import me.znotchill.lime.servers.Server

data class ServerConnectionResponse(
    val server: Server,
    val socket: Socket
)
