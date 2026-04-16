package packets

import kotlinx.io.Source

object PacketRegistry {
    private val decoders = mutableMapOf<Int, (Source) -> MinecraftPacket>()

    fun get(id: Int): ((Source) -> MinecraftPacket)? {
        return decoders[id]
    }

    fun register(id: Int, decoder: (Source) -> MinecraftPacket) {
        decoders[id] = decoder
    }

    fun decode(id: Int, source: Source): MinecraftPacket? {
        val decoder = decoders[id] ?: return null
        return decoder(source)
    }
}