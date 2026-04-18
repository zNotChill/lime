package me.znotchill.lime.packets

import kotlinx.io.Source
import me.znotchill.lime.ConnectionState
import me.znotchill.lime.generated.Identifiable
import me.znotchill.lime.registries.PacketProtocolRegistry

object PacketRegistry {
    private val decoders = mutableMapOf<String, (Source) -> MinecraftPacket>()

    fun register(
        state: ConnectionState,
        direction: Int,
        packet: Identifiable,
        decoder: (Source) -> MinecraftPacket
    ) {
        val supportedVersions = listOf(774, 763)

        for (v in supportedVersions) {
            val dynamicId = PacketProtocolRegistry.getId(v, state, direction, packet.value)
            if (dynamicId != null) {
                decoders["${v}_${state.name}_${direction}_$dynamicId"] = decoder
            }
        }
    }

    fun get(version: Int, state: ConnectionState, direction: Int, id: Int): ((Source) -> MinecraftPacket)? {
        return decoders["${version}_${state.name}_${direction}_$id"]
    }
}