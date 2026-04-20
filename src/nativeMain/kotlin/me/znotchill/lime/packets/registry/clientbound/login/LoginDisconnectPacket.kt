package me.znotchill.lime.packets.registry.clientbound.login

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.components.Component
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readMcString
import me.znotchill.lime.packets.writeMcString

class LoginDisconnectPacket(
    val component: Component,
) : ClientPacket {
    constructor(text: String) : this(Component.text(text))

    override val id = Packet.Clientbound.Login.Disconnect
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.LOGIN,
            PipeDirection.CLIENT,
            Packet.Clientbound.Login.Disconnect,
            ::decode
        )

        fun decode(source: Source): LoginDisconnectPacket {
            val json = source.readMcString()
            return try {
                LoginDisconnectPacket(Component.fromJson(json))
            } catch (e: Exception) {
                LoginDisconnectPacket("Failed to parse kick message: $json")
            }
        }
    }

    override fun encode(output: Sink) {
        output.writeMcString(component.toJson())
    }
}