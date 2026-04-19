package me.znotchill.lime.packets.registry.clientbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.ConnectionState
import me.znotchill.lime.components.Component
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.writeMcString
import me.znotchill.lime.packets.writeVarInt
import me.znotchill.lime.packets.writeBoolean

data class Match(
    val text: String,
    val tooltip: Component? = null
)

class TabCompleteResponsePacket(
    val transactionId: Int,
    val start: Int,
    val length: Int,
    val matches: List<Match>
) : ClientPacket {
    override val id = Packet.Clientbound.Play.TabComplete
    override val state = ConnectionState.PLAY

    companion object {
        fun init() = PacketRegistry.register(ConnectionState.PLAY, 0, Packet.Clientbound.Play.TabComplete, ::decode)

        fun decode(packet: Source): TabCompleteResponsePacket {
            return TabCompleteResponsePacket(0, 0, 0, emptyList())
        }
    }

    override fun encode(output: Sink) {
        output.writeVarInt(transactionId)
        output.writeVarInt(start)
        output.writeVarInt(length)
        output.writeVarInt(matches.size)
        for (match in matches) {
            output.writeMcString(match.text)
            if (match.tooltip != null) {
                output.writeBoolean(true)
                match.tooltip.writeNbt(output)
            } else {
                output.writeBoolean(false)
            }
        }
    }
}