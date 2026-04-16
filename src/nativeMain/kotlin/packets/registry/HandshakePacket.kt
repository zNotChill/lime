package packets.registry

import io.ktor.utils.io.core.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readUShort
import kotlinx.io.writeUShort
import packets.MinecraftPacket
import packets.readMcString
import packets.readVarInt
import packets.writeMcString
import packets.writeVarInt

data class HandshakePacket(
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: Int,
    val nextState: Int
) : MinecraftPacket {
    override val id = 0x00

    companion object {
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