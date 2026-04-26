package me.znotchill.lime.packets.registry.clientbound.login

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.*

class LoginPluginRequestPacket(
    val messageId: Int,
    val channel: String,
    val data: ByteArray
) : ClientPacket {
    override val id = Packet.Clientbound.Login.LoginPluginRequest
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.LOGIN,
            PipeDirection.CLIENT,
            Packet.Clientbound.Login.LoginPluginRequest,
            ::decode
        )

        fun decode(packet: Source): LoginPluginRequestPacket {
            val messageId = packet.readVarInt()
            val channel = packet.readMcString()
            val data = packet.readByteArray()

            return LoginPluginRequestPacket(
                messageId, channel, data
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeVarInt(messageId)
        output.writeMcString(channel)
        output.writeBytes(data.toList())
    }
}