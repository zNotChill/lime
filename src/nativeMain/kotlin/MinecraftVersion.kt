import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable(with = MinecraftVersionSerializer::class)
enum class MinecraftVersion(val protocol: Int) {
    `1_21_11`(774);
}

object MinecraftVersionSerializer : KSerializer<MinecraftVersion> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MinecraftVersion", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: MinecraftVersion) {
        encoder.encodeInt(value.protocol)
    }

    override fun deserialize(decoder: Decoder): MinecraftVersion {
        val protocol = decoder.decodeInt()
        return MinecraftVersion.entries.first { it.protocol == protocol }
    }
}