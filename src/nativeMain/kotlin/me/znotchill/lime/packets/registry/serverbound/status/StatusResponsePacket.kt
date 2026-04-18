package me.znotchill.lime.packets.registry.serverbound.status

import me.znotchill.lime.json
import kotlinx.io.Sink
import me.znotchill.lime.ConnectionState
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.payloads.StatusPayload
import me.znotchill.lime.packets.writeMcString

data class StatusResponsePacket(
    val payload: StatusPayload
) : ClientPacket {
    override val id = Packet.Clientbound.Status.ServerInfo
    override val state = ConnectionState.STATUS

    override fun encode(output: Sink) {
        val jsonString = json.encodeToString(payload)
        output.writeMcString(jsonString)
    }
}