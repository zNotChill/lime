package me.znotchill.lime.packets.registry.serverbound.configuration

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry

class FinishConfigurationPacket : ClientPacket {
    override val id = Packet.Serverbound.Config.FinishConfiguration
    override val state = ConnectionState.CONFIGURATION

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.CONFIGURATION,
            PipeDirection.SERVER,
            Packet.Serverbound.Config.FinishConfiguration,
            ::decode
        )

        fun decode(packet: Source): FinishConfigurationPacket {
            return FinishConfigurationPacket()
        }
    }

    override fun encode(output: Sink) {
    }
}
