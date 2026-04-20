package me.znotchill.lime.packets.registry.clientbound.configuation

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.components.Component
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry

class ConfigDisconnectPacket(
    val component: Component,
) : ClientPacket {
    constructor(text: String) : this(Component.text(text))

    override val id = Packet.Clientbound.Config.Disconnect
    override val state = ConnectionState.CONFIGURATION

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.CONFIGURATION,
            PipeDirection.CLIENT,
            Packet.Clientbound.Config.Disconnect,
            ::decode
        )

        fun decode(packet: Source): ConfigDisconnectPacket {
            return ConfigDisconnectPacket("")
        }
    }

    override fun encode(output: Sink) {
        component.writeNbt(output)
    }
}