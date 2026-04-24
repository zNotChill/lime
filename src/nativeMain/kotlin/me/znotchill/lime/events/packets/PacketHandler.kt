package me.znotchill.lime.events.packets

import me.znotchill.lime.packets.MinecraftPacket

fun interface PacketHandler<T : MinecraftPacket> {
    suspend fun handle(context: PacketEventContext<T>)
}