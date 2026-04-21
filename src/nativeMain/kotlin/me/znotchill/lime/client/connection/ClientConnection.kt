package me.znotchill.lime.client.connection

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.client.ServerConnectionResponse
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.MinecraftPacket
import me.znotchill.lime.servers.ServerManager
import me.znotchill.lime.utils.toSocketAddress

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
            val server = ConfigManager.server.servers.servers[serverName]
            if (server == null) {
                log.e("Misconfigured server \"$serverName\"! Defined in try list, but the server config is absent!")
                player.disconnect("Misconfigured server! Check logs for more info!")
                return null
            }

            val address = server.toSocketAddress()

            val serverSocket = try {
                log.i("Connecting to $address")
                withTimeout(ConfigManager.server.socketTimeout) {
                    aSocket(selector).tcp().connect(address.host, address.port)
                }
            } catch (e: Exception) {
                log.e("Cannot connect to ${address.host}:${address.port}: ${e.message}")
                null
            }

            val serverObject = ServerManager.get(serverName)
            if (serverObject == null) {
                // this is a big issue if we reach this
                log.e("Misconfigured server \"$serverName\"! Defined in try list and hosts but the server is not registered!")
                player.disconnect("Server is not registered! Check logs for more info!")
                return null
            }

            if (serverSocket != null) {
                return ServerConnectionResponse(
                    address,
                    serverObject,
                    serverSocket
                )
            }
        }

        return null
    }
}