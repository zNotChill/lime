package me.znotchill.lime.nbt

import kotlinx.io.Sink
import kotlinx.io.writeDouble
import kotlinx.io.writeFloat

sealed class NbtTag {
    abstract fun writePayload(output: Sink)
    abstract val typeId: Byte

    fun writeNamed(output: Sink, name: String) {
        output.writeByte(typeId)
        val nameBytes = name.encodeToByteArray()
        output.writeShort(nameBytes.size.toShort())
        output.write(nameBytes)
        writePayload(output)
    }
}

data class NbtByte(val value: Byte) : NbtTag() {
    override val typeId = 0x01.toByte()
    override fun writePayload(output: Sink) { output.writeByte(value) }
}

data class NbtShort(val value: Short) : NbtTag() {
    override val typeId = 0x02.toByte()
    override fun writePayload(output: Sink) { output.writeShort(value) }
}

data class NbtInt(val value: Int) : NbtTag() {
    override val typeId = 0x03.toByte()
    override fun writePayload(output: Sink) { output.writeInt(value) }
}

data class NbtLong(val value: Long) : NbtTag() {
    override val typeId = 0x04.toByte()
    override fun writePayload(output: Sink) { output.writeLong(value) }
}

data class NbtFloat(val value: Float) : NbtTag() {
    override val typeId = 0x05.toByte()
    override fun writePayload(output: Sink) { output.writeFloat(value) }
}

data class NbtDouble(val value: Double) : NbtTag() {
    override val typeId = 0x06.toByte()
    override fun writePayload(output: Sink) { output.writeDouble(value) }
}

data class NbtString(val value: String) : NbtTag() {
    override val typeId = 0x08.toByte()
    override fun writePayload(output: Sink) {
        val bytes = value.encodeToByteArray()
        output.writeShort(bytes.size.toShort())
        output.write(bytes)
    }

    fun writeInline(output: Sink) {
        output.writeByte(typeId)
        writePayload(output)
    }
}

data class NbtBoolean(val value: Boolean) : NbtTag() {
    override val typeId = 0x01.toByte()
    override fun writePayload(output: Sink) { output.writeByte(if (value) 1 else 0) }
}

class NbtList<T : NbtTag>(val elementTypeId: Byte, val elements: List<T>) : NbtTag() {
    override val typeId = 0x09.toByte()
    override fun writePayload(output: Sink) {
        output.writeByte(elementTypeId)
        output.writeInt(elements.size)
        for (element in elements) element.writePayload(output)
    }
}

class NbtCompound(private val tags: Map<String, NbtTag> = emptyMap()) : NbtTag() {
    override val typeId = 0x0A.toByte()

    override fun writePayload(output: Sink) {
        for ((name, tag) in tags) tag.writeNamed(output, name)
        output.writeByte(0x00)
    }

    fun writeInline(output: Sink) {
        output.writeByte(typeId)
        writePayload(output)
    }
}

fun nbtCompound(block: NbtCompoundBuilder.() -> Unit): NbtCompound {
    return NbtCompoundBuilder().apply(block).build()
}

class NbtCompoundBuilder {
    private val tags = mutableMapOf<String, NbtTag>()

    fun string(name: String, value: String) {
        tags[name] = NbtString(value)
    }
    fun byte(name: String, value: Byte) {
        tags[name] = NbtByte(value)
    }
    fun short(name: String, value: Short) {
        tags[name] = NbtShort(value)
    }
    fun int(name: String, value: Int) {
        tags[name] = NbtInt(value)
    }
    fun long(name: String, value: Long) {
        tags[name] = NbtLong(value)
    }
    fun float(name: String, value: Float) {
        tags[name] = NbtFloat(value)
    }
    fun double(name: String, value: Double) {
        tags[name] = NbtDouble(value)
    }
    fun boolean(name: String, value: Boolean) {
        tags[name] = NbtBoolean(value)
    }

    fun compound(name: String, block: NbtCompoundBuilder.() -> Unit) {
        tags[name] = NbtCompoundBuilder().apply(block).build()
    }

    fun <T : NbtTag> list(name: String, elementTypeId: Byte, elements: List<T>) {
        tags[name] = NbtList(elementTypeId, elements)
    }

    fun compounds(name: String, elements: List<NbtCompound>) {
        tags[name] = NbtList(0x0A.toByte(), elements)
    }

    fun build() = NbtCompound(tags)
}