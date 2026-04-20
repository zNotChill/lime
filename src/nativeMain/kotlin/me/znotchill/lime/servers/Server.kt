package me.znotchill.lime.servers

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.utils.SocketAddress

data class Server(
    val name: String,
    val address: SocketAddress,
    val isVelocity: Boolean = false
) {
    var players: MutableList<MinecraftPlayer> = mutableListOf()
}
