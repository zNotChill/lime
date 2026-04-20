package me.znotchill.lime.packets.registry.clientbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.*

class StartConfigurationPacket() : ClientPacket {
    override val id = Packet.Clientbound.Play.StartConfiguration
    override val state = ConnectionState.PLAY

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.PLAY,
            PipeDirection.CLIENT,
            Packet.Clientbound.Play.StartConfiguration,
            ::decode
        )

        fun decode(packet: Source): StartConfigurationPacket {
            return StartConfigurationPacket()
        }
    }

    override fun encode(output: Sink) {
    }
}