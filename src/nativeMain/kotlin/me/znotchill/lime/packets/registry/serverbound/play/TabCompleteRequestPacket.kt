package me.znotchill.lime.packets.registry.serverbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readMcString
import me.znotchill.lime.packets.readVarInt
import me.znotchill.lime.packets.writeMcString
import me.znotchill.lime.packets.writeVarInt

class TabCompleteRequestPacket(
    val transactionId: Int,
    var text: String
) : ClientPacket {
    override val id = Packet.Serverbound.Play.TabComplete
    override val state = ConnectionState.PLAY

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.PLAY,
            PipeDirection.SERVER,
            Packet.Serverbound.Play.TabComplete,
            ::decode
        )

        fun decode(packet: Source): TabCompleteRequestPacket {
            return TabCompleteRequestPacket(
                transactionId = packet.readVarInt(),
                text = packet.readMcString()
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeVarInt(transactionId)
        output.writeMcString(text)
    }
}