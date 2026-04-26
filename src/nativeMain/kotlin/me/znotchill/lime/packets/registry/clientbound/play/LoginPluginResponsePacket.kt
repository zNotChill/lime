package me.znotchill.lime.packets.registry.clientbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.*

class LoginPluginResponsePacket(
    val messageId: Int,
    val data: ByteArray
) : ClientPacket {
    override val id = Packet.Serverbound.Login.LoginPluginResponse
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.LOGIN,
            PipeDirection.SERVER,
            Packet.Serverbound.Login.LoginPluginResponse,
            ::decode
        )

        fun decode(packet: Source): LoginPluginResponsePacket {
            val messageId = packet.readVarInt()
            packet.readBoolean()
            val dataSize = packet.readVarInt()
            val data = packet.readByteArray(dataSize)

            return LoginPluginResponsePacket(
                messageId, data
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeVarInt(messageId)
        output.writeBoolean(true)
        output.write(data)
    }
}