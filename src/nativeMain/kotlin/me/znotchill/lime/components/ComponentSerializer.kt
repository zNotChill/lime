package me.znotchill.lime.components

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ComponentSerializer : KSerializer<Component> {
    override val descriptor = PrimitiveSerialDescriptor("Component", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Component) {
        encoder.encodeString(value.toJson())
    }

    override fun deserialize(decoder: Decoder): Component {
        return Component.fromJson(decoder.decodeString())
    }
}