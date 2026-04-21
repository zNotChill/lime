package me.znotchill.lime.resolvers

import io.ktor.network.sockets.Socket
import me.znotchill.lime.utils.SocketAddress

data class ResolverResponse(
    val success: Boolean = false,
    val failReason: String? = null,
    val address: SocketAddress? = null,
    val socket: Socket? = null
)
