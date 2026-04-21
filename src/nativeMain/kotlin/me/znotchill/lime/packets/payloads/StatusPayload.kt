package me.znotchill.lime.packets.payloads

import me.znotchill.lime.MinecraftVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StatusPayload(
    val version: StatusVersion,
    val players: StatusPlayers,
    val description: JsonElement,
)

@Serializable
data class StatusVersion(
    val name: String = "",
    val protocol: MinecraftVersion = MinecraftVersion.`1_21_11`
)

@Serializable
data class StatusPlayers(
    val max: Int = 0,
    val online: Int = 0
)