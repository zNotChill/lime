package me.znotchill.lime.events

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.packets.MinecraftPacket

class PacketEventContext<T : MinecraftPacket>(
    val player: MinecraftPlayer,
    val packet: T
) {
    var isCancelled: Boolean = false
    
    fun cancel() {
        isCancelled = true
    }
}