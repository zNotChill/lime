package me.znotchill.lime.events

import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.packets.registry.serverbound.play.ChatPacket

object DefaultEvents {
    fun register() {
        PacketEventManager.register<ChatPacket> { event ->
            event.cancel()
            println("[${event.player.username}]: ${event.packet.message}")
        }
        PacketEventManager.register<LoginStartPacket> { event ->
            event.player.username = event.packet.name
            event.player.uuid = event.packet.uuid
        }
    }
}