package me.znotchill.lime.packets.payloads

import me.znotchill.lime.MinecraftVersion
import kotlinx.serialization.Serializable

@Serializable
data class StatusPayload(
    val version: StatusVersion,
    val players: StatusPlayers,
    val description: StatusDescription,
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

@Serializable
data class StatusDescription(
    val text: String
)