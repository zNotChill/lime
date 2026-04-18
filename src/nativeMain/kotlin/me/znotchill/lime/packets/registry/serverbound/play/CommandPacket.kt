package me.znotchill.lime.packets.registry.serverbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.ConnectionState
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readMcString
import me.znotchill.lime.packets.writeMcString

class CommandPacket(
    val command: String,
) : ClientPacket {
    override val id = Packet.Serverbound.Play.ChatCommand
    override val state = ConnectionState.PLAY

    companion object {
        fun init() = PacketRegistry.register(ConnectionState.PLAY, 1, Packet.Serverbound.Play.ChatCommand, ::decode)

        fun decode(packet: Source): CommandPacket {
            return CommandPacket(
                packet.readMcString()
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeMcString(command)
    }
}