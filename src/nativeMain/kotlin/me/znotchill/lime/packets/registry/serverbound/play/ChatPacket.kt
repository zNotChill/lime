package me.znotchill.lime.packets.registry.serverbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readMcString
import me.znotchill.lime.packets.readVarInt
import me.znotchill.lime.packets.writeMcString
import me.znotchill.lime.packets.writeVarInt

class ChatPacket(
    val message: String,
    val timestamp: Long,
    val salt: Long,
    val signature: ByteArray?,
    val messageCount: Int,
    val acknowledged: ByteArray,
    val checksum: Byte,
) : ClientPacket {
    override val id = Packet.Serverbound.Play.ChatMessage
    override val state = ConnectionState.PLAY

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.PLAY,
            PipeDirection.SERVER,
            Packet.Serverbound.Play.ChatMessage,
            ::decode
        )

        fun decode(packet: Source): ChatPacket {
            val message = packet.readMcString()
            val timestamp = packet.readLong()
            val salt = packet.readLong()

            val hasSignature = packet.readByte().toInt() != 0
            val signature = if (hasSignature) {
                packet.readByteArray(256)
            } else null

            val messageCount = packet.readVarInt()

            val acknowledged = packet.readByteArray(3)
            val checksum = packet.readByte()

            return ChatPacket(
                message = message,
                timestamp = timestamp,
                salt = salt,
                signature = signature,
                messageCount = messageCount,
                acknowledged = acknowledged,
                checksum = checksum
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeMcString(message)
        output.writeLong(timestamp)
        output.writeLong(salt)

        if (signature != null) {
            output.writeByte(1)
            output.write(signature)
        } else {
            output.writeByte(0)
        }

        output.writeVarInt(messageCount)
        output.write(acknowledged)
    }
}