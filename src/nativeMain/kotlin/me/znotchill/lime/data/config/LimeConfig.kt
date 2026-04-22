package me.znotchill.lime.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LimeConfig(
    @SerialName("version") val configVersion: String = "1.0",

    val socketTimeout: Long = 5000L,
    val status: StatusConfig,

    val servers: ServersConfig,
)

@Serializable
data class ServersConfig(
    val servers: Map<String, String>,
    val tryList: List<String>
)

@Serializable
data class StatusConfig(
    val bind: String = "127.0.0.1:25565",
    val motd: String = "<#99e550>A Lime Server",
    @SerialName("max-players") val showMaxPlayers: Int = 500,
)