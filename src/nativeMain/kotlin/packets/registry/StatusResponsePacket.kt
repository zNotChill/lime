package packets.registry

import json
import kotlinx.io.Sink
import packets.MinecraftPacket
import packets.payloads.StatusPayload
import packets.writeMcString

data class StatusResponsePacket(
    val payload: StatusPayload
) : MinecraftPacket {
    override val id = 0x00

    override fun encode(output: Sink) {
        val jsonString = json.encodeToString(payload)
        output.writeMcString(jsonString)
    }
}