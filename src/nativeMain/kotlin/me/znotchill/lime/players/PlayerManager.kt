package me.znotchill.lime.players

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.exceptions.AlreadyExistsException
import me.znotchill.lime.exceptions.DoesNotExistException
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.utils.UUID

object PlayerManager : Loggable {
    override val loggerTag = "Player"
    private val players: MutableMap<UUID, MinecraftPlayer> = mutableMapOf()

    fun count() = players.size
    fun list() = players
    fun get(uuid: UUID) = players[uuid]
    fun add(player: MinecraftPlayer) {
        if (get(player.uuid) != null)
            throw AlreadyExistsException("Player ${player.username} (${player.uuid}) is already online!")

        players[player.uuid] = player
    }
    fun remove(player: MinecraftPlayer) {
        if (get(player.uuid) == null)
            throw DoesNotExistException("Player ${player.username} (${player.uuid}) is not online!")

        players.remove(player.uuid)
    }
}