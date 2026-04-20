package me.znotchill.lime.packets.registry.serverbound.handshake

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readUShort
import kotlinx.io.writeUShort
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readMcString
import me.znotchill.lime.packets.readVarInt
import me.znotchill.lime.packets.writeMcString
import me.znotchill.lime.packets.writeVarInt

data class HandshakePacket(
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: Int,
    val nextState: Int
) : ClientPacket {
    override val id = Packet.Serverbound.Handshake.Intention
    override val state = ConnectionState.HANDSHAKE

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.HANDSHAKE,
            PipeDirection.SERVER,
            Packet.Serverbound.Handshake.Intention,
            ::decode
        )

        fun decode(packet: Source): HandshakePacket {
            return HandshakePacket(
                protocolVersion = packet.readVarInt(),
                serverAddress = packet.readMcString(),
                serverPort = packet.readUShort().toInt(),
                nextState = packet.readVarInt()
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeVarInt(protocolVersion)
        output.writeMcString(serverAddress)
        output.writeUShort(serverPort.toUShort())
        output.writeVarInt(nextState)
    }
}