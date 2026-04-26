package me.znotchill.lime.packets.registry.clientbound.login

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readVarInt
import me.znotchill.lime.packets.writeVarInt

data class SetCompressionPacket(
    val threshold: Int
) : ClientPacket {
    override val id = Packet.Clientbound.Login.Compress
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.LOGIN,
            PipeDirection.CLIENT,
            Packet.Clientbound.Login.Compress,
            ::decode
        )

        fun decode(packet: Source): SetCompressionPacket {
            return SetCompressionPacket(
                threshold = packet.readVarInt()
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeVarInt(threshold)
    }
}