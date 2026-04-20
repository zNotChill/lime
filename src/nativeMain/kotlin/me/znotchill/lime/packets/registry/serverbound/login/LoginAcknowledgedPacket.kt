package me.znotchill.lime.packets.registry.serverbound.login

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry

class LoginAcknowledgedPacket() : ClientPacket {
    override val id = Packet.Serverbound.Login.LoginAcknowledged
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.LOGIN,
            PipeDirection.SERVER,
            Packet.Serverbound.Login.LoginAcknowledged,
            ::decode
        )

        fun decode(packet: Source): LoginAcknowledgedPacket {
            return LoginAcknowledgedPacket()
        }
    }

    override fun encode(output: Sink) {}
}