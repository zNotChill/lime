package me.znotchill.lime.packets.registry.serverbound.login

import kotlinx.io.Sink
import kotlinx.io.Source
import me.znotchill.lime.ConnectionState
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.readMcString
import me.znotchill.lime.packets.readUUID
import me.znotchill.lime.packets.writeMcString
import me.znotchill.lime.packets.writeUUID
import me.znotchill.lime.utils.UUID

class LoginStartPacket(
    val name: String,
    val uuid: UUID,
) : ClientPacket {
    override val id = Packet.Serverbound.Login.LoginStart
    override val state = ConnectionState.LOGIN

    companion object {
        fun init() = PacketRegistry.register(ConnectionState.LOGIN, 1, Packet.Serverbound.Login.LoginStart, ::decode)

        fun decode(packet: Source): LoginStartPacket {
            return LoginStartPacket(
                packet.readMcString(),
                packet.readUUID()
            )
        }
    }

    override fun encode(output: Sink) {
        output.writeMcString(name)
        output.writeUUID(uuid)
    }
}