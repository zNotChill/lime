package me.znotchill.lime.resolvers

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.withTimeout
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.servers.ServerManager
import me.znotchill.lime.utils.toSocketAddress

object LimeServerResolver : ServerResolver {
    override val loggerTag = "LimeServerResolver"
    override suspend fun resolveServer(
        name: String,
        player: MinecraftPlayer?,
        selector: SelectorManager
    ): ResolverResponse {
        val server = ConfigManager.server.servers.servers[name]
        if (server == null) {
            log.e("Misconfigured server \"$name\"! Defined in try list, but the server config is absent!")
            player?.disconnect("Misconfigured server! Check logs for more info!")
            return ResolverResponse(
                failReason = "Misconfigured server! Check logs for more info!"
            )
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

        val serverObject = ServerManager.get(name)
        if (serverObject == null) {
            // this is a big issue if we reach this
            log.e("Misconfigured server \"$name\"! Defined in try list and hosts but the server is not registered!")
            player?.disconnect("Server is not registered! Check logs for more info!")
            return ResolverResponse(
                failReason = "Server is not registered! Check logs for more info!"
            )
        }

        if (serverSocket != null) {
            return ResolverResponse(
                success = true,
                address = address,
                socket = serverSocket
            )
        }

        return ResolverResponse(
            failReason = "Server socket could not be created!"
        )
    }
}