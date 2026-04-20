package me.znotchill.lime.utils

import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.TcpSocketBuilder

suspend fun TcpSocketBuilder.bind(
    socketAddress: SocketAddress,
    configure: SocketOptions.AcceptorOptions.() -> Unit = {}
): ServerSocket {
    return bind(
        InetSocketAddress(
            socketAddress.host,
            socketAddress.port
        ),
        configure
    )
}

suspend fun TcpSocketBuilder.connect(
    socketAddress: SocketAddress,
    configure: SocketOptions.AcceptorOptions.() -> Unit = {}
): ServerSocket {
    return bind(socketAddress, configure)
}