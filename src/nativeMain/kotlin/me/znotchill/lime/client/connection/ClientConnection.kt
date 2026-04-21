package me.znotchill.lime.client.connection

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.CoroutineScope
import me.znotchill.lime.LimeProxy
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.client.ServerConnectionResponse
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.MinecraftPacket
import me.znotchill.lime.servers.Server

class ClientConnection(
    socket: Socket,
    parentScope: CoroutineScope,
) : Connection(socket, parentScope) {
    lateinit var player: MinecraftPlayer
    override val loggerTag: String
        get() = if (::player.isInitialized) {
            "Conn@${player.username}"
        } else {
            "Conn@Player"
        }

    suspend fun sendPacket(packet: MinecraftPacket) {
        val state = (packet as? ClientPacket)?.state ?: ConnectionState.STATUS

        sendPacket(
            packet = packet,
            state = state,
            direction = PipeDirection.CLIENT,
            protocol = this.protocol
        )
    }

    // TODO: make this generic and non-player dependent, so when I implement
    // server shutdowns, we can find a server and connect all players to it at once
    // instead of trying all servers for each player
    suspend fun tryAllServers(selector: SelectorManager): ServerConnectionResponse? {
        val tryList = ConfigManager.server.servers.tryList
        if (tryList.isEmpty()) {
            player.disconnect("No configured try ist!")
            return null
        }

        tryList.forEach { serverName ->
            val resolveResult = LimeProxy.defaultResolver.resolveServer(
                serverName,
                player = null,
                selector
            )

            if (resolveResult.failReason != null) {
                player.disconnect(resolveResult.failReason)
                return@forEach
            }

            if (!resolveResult.success) return@forEach
            if (resolveResult.socket == null) return@forEach
            if (resolveResult.address == null) return@forEach

            return ServerConnectionResponse(
                server = Server(
                    name = serverName,
                    address = resolveResult.address,
                    isVelocity = false
                ),
                socket = resolveResult.socket
            )
        }

        return null
    }
}