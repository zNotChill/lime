package me.znotchill.lime.client.handlers

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.connection.BackendConnection
import me.znotchill.lime.events.proxy.ProxyEventManager
import me.znotchill.lime.events.proxy.registry.PlayerJoinEvent

object LoginHandler {
    suspend fun completeLogin(player: MinecraftPlayer) {
        val client = player.pipeline.client
        val selector = player.pipeline.selector

        val response = client.tryAllServers(selector) ?: return

        val newBackend = BackendConnection(response.socket, player.pipeline.scope)
        player.pipeline.backend = newBackend
        player.remoteConnection = newBackend
        player.currentServer = response.server

        player.currentServer.addPlayer(player)

        player.pipeline.awaitingEncryptionResponse = false
        player.pipeline.startBackendReader(player)

        player.handshakePacket.let {
            newBackend.sendRawPacket(it.id, it.data)
        }

        newBackend.sendRawPacket(player.loginStartPacket.id, player.loginStartPacket.data)

        ProxyEventManager.emit(
            player,
            PlayerJoinEvent()
        )
    }
}