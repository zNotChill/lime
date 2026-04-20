package me.znotchill.lime.packets.registry.clientbound.login

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.ConnectionState
import me.znotchill.lime.components.Component
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.writeMcString

class LoginDisconnectPacket(
    val component: Component,
) : ClientPacket {
    constructor(text: String) : this(Component.text(text))

    override val id = Packet.Clientbound.Login.Disconnect
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(ConnectionState.LOGIN, 0, Packet.Clientbound.Login.Disconnect, ::decode)

        fun decode(packet: Source): LoginDisconnectPacket {
            return LoginDisconnectPacket("")
        }
    }

    override fun encode(output: Sink) {
        output.writeMcString(component.toJson())
    }
}