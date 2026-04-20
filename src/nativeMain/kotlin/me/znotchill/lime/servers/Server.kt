package me.znotchill.lime.servers

import me.znotchill.lime.MinecraftPlayer

data class Server(
    val name: String,
    val ip: String,
    val port: Int,
    val isVelocity: Boolean = false
) {
    var players: MutableList<MinecraftPlayer> = mutableListOf()
}
