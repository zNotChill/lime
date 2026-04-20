package me.znotchill.lime.packets.registry.clientbound.status

import kotlinx.io.Sink
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket

data class PingResponsePacket(
    val time: Long
) : ClientPacket {
    override val id = Packet.Clientbound.Status.Ping
    override val state = ConnectionState.STATUS

    override fun encode(output: Sink) {
        output.writeLong(time)
    }
}