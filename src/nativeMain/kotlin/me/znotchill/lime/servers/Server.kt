package me.znotchill.lime.servers

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.exceptions.AlreadyExistsException
import me.znotchill.lime.utils.SocketAddress
import me.znotchill.lime.utils.UUID
import kotlin.collections.set

data class Server(
    val name: String,
    val address: SocketAddress,
    val isVelocity: Boolean = false
) {
    val players: MutableMap<UUID, MinecraftPlayer> = mutableMapOf()

    fun getPlayer(uuid: UUID) = players[uuid]
    fun addPlayer(player: MinecraftPlayer) {
        if (getPlayer(player.uuid) != null)
            throw AlreadyExistsException("Player ${player.username} (${player.uuid}) is already registered to this server!")

        players[player.uuid] = player
    }
}
