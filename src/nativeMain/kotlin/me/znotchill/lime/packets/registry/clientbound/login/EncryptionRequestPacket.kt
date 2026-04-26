package me.znotchill.lime.packets.registry.clientbound.login

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.*

data class EncryptionRequestPacket(
    val serverId: String,
    val publicKey: List<Byte>,
    val verifyToken: List<Byte>,
    val authenticate: Boolean = true
) : ClientPacket {
    override val id = Packet.Clientbound.Login.EncryptionBegin
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.LOGIN,
            PipeDirection.CLIENT,
            Packet.Clientbound.Login.EncryptionBegin,
            ::decode
        )

        fun decode(packet: Source): EncryptionRequestPacket {
            return EncryptionRequestPacket(
                serverId = packet.readMcString(),
                publicKey = packet.readPrefixedBytes(),
                verifyToken = packet.readPrefixedBytes(),
                authenticate = packet.readBoolean()
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeMcString(serverId)
        output.writeBytes(publicKey)
        output.writeBytes(verifyToken)
        output.writeBoolean(authenticate)
    }
}