package me.znotchill.lime.packets.registry.clientbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.ConnectionState
import me.znotchill.lime.components.Component
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.*

class SystemChatPacket(
    val component: Component,
    val overlay: Boolean = false
) : ClientPacket {

    constructor(text: String, overlay: Boolean = false) : this(Component.text(text), overlay)

    override val id = Packet.Clientbound.Play.SystemChat
    override val state = ConnectionState.PLAY

    companion object {
        fun init() = PacketRegistry.register(ConnectionState.PLAY, 0, Packet.Clientbound.Play.SystemChat, ::decode)

        fun decode(packet: Source): SystemChatPacket {
            return SystemChatPacket("")
        }
    }

    override fun encode(output: Sink) {
        component.writeNbt(output)
        output.writeBoolean(overlay)
    }
}