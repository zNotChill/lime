package me.znotchill.lime.packets.registry.serverbound.status

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry

class PingRequestPacket(
    val time: Long
) : ClientPacket {
    override val id = Packet.Serverbound.Status.Ping
    override val state = ConnectionState.STATUS

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.STATUS,
            PipeDirection.SERVER,
            Packet.Serverbound.Status.Ping,
            ::decode
        )

        fun decode(packet: Source) = PingRequestPacket(
            packet.readLong()
        )
    }

    override fun encode(output: Sink) {}
}