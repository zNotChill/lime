package me.znotchill.lime.events.packets

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.packets.MinecraftPacket
import me.znotchill.lime.packets.RawPacket

class PacketEventContext<T : MinecraftPacket>(
    val player: MinecraftPlayer,
    val packet: T,
    val rawPacket: RawPacket,
) {
    var isCancelled: Boolean = false
    
    fun cancel() {
        isCancelled = true
    }
}