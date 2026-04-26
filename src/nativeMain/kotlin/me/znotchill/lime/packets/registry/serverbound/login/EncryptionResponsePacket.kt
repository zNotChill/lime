package me.znotchill.lime.packets.registry.serverbound.login

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readPrefixedBytes
import me.znotchill.lime.packets.writeBytes

data class EncryptionResponsePacket(
    val sharedSecret: List<Byte>,
    val verifyToken: List<Byte>
) : ClientPacket {
    override val id = Packet.Serverbound.Login.EncryptionBegin
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.LOGIN,
            PipeDirection.SERVER,
            Packet.Serverbound.Login.EncryptionBegin,
            ::decode
        )

        fun decode(packet: Source): EncryptionResponsePacket {
            return EncryptionResponsePacket(
                sharedSecret = packet.readPrefixedBytes(),
                verifyToken = packet.readPrefixedBytes()
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeBytes(sharedSecret)
        output.writeBytes(verifyToken)
    }
}