package me.znotchill.lime.packets.registry.clientbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.components.Component
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.*

class PlayDisconnectPacket(
    val component: Component,
) : ClientPacket {
    constructor(text: String) : this(Component.text(text))

    override val id = Packet.Clientbound.Play.KickDisconnect
    override val state = ConnectionState.PLAY

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.PLAY,
            PipeDirection.CLIENT,
            Packet.Clientbound.Play.KickDisconnect,
            ::decode
        )

        fun decode(packet: Source): PlayDisconnectPacket {
            return PlayDisconnectPacket("")
        }
    }

    override fun encode(output: Sink) {
        component.writeNbt(output)
    }
}