package me.znotchill.lime.packets

import dev.whyoleg.cryptography.bigint.BigInt
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.generated.Identifiable
import me.znotchill.lime.utils.UUID

interface MinecraftPacket {
    val id: Identifiable
    fun encode(output: Sink)
}

interface ClientPacket : MinecraftPacket {
    override val id: Identifiable
    val state: ConnectionState

    override fun encode(output: Sink)
}

suspend fun ByteReadChannel.readVarInt(): Int {
    var numRead = 0
    var result = 0
    while (true) {
        val read = readByte().toInt()
        result = result or (read and 0x7F shl (7 * numRead))
        numRead++
        if (numRead > 5) error("VarInt too big")
        if (read and 0x80 == 0) break
    }
    return result
}

fun Source.readVarInt(): Int {
    var numRead = 0
    var result = 0
    var read: Byte
    do {
        read = readByte()
        val value = (read.toInt() and 0x7F)
        result = result or (value shl (7 * numRead))
        numRead++
        if (numRead > 5) throw RuntimeException("VarInt is too big")
    } while ((read.toInt() and 0x80) != 0)
    return result
}

fun Sink.writeVarInt(value: Int) {
    var v = value
    while (true) {
        if ((v and 0x7F.inv()) == 0) {
            writeByte(v.toByte())
            return
        }
        writeByte((v and 0x7F or 0x80).toByte())
        v = v ushr 7
    }
}

suspend fun ByteWriteChannel.writeVarInt(value: Int) {
    var v = value
    while (true) {
        if ((v and 0x7F.inv()) == 0) {
            writeByte(v.toByte())
            return
        }
        writeByte((v and 0x7F or 0x80).toByte())
        v = v ushr 7
    }
}

suspend fun ByteWriteChannel.writeMcString(value: String) {
    val bytes = value.encodeToByteArray()
    writeVarInt(bytes.size)
    writeFully(bytes)
}

fun getVarIntSize(value: Int): Int {
    var v = value
    var size = 0
    do {
        v = v ushr 7
        size++
    } while (v != 0)
    return size
}

fun Sink.writeMcString(value: String) {
    val bytes = value.encodeToByteArray()
    writeVarInt(bytes.size)
    writeFully(bytes)
}

fun Source.readMcString(): String {
    val length = this.readVarInt()
    return this.readByteArray(length).decodeToString()
}

fun Sink.writeUUID(uuid: UUID) {
    writeLong(uuid.mostSignificantBits)
    writeLong(uuid.leastSignificantBits)
}

fun Source.readUUID(): UUID {
    return UUID(readLong(), readLong())
}

fun Sink.writeBoolean(value: Boolean) {
    writeByte(if (value) 1 else 0)
}

fun Source.readBoolean(): Boolean {
    return readByte().toInt() != 0
}

fun Sink.writeBytes(bytes: List<Byte>) {
    writeVarInt(bytes.size)
    bytes.forEach { writeByte(it) }
}

fun Source.readPrefixedBytes(): List<Byte> {
    val length = readVarInt()
    return readByteArray(length).toList()
}

fun ByteArray.toNotchianHex(): String {
    val negative = this[0].toInt() and 0x80 != 0
    val bytes = if (negative) twosComplement() else this
    val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return (if (negative) "-" else "") + hex.trimStart('0')
}

private fun ByteArray.twosComplement(): ByteArray {
    val result = this.copyOf()
    var carry = true
    for (i in result.indices.reversed()) {
        val v = (result[i].toInt() and 0xFF).inv() + (if (carry) 1 else 0)
        result[i] = (v and 0xFF).toByte()
        carry = v > 0xFF
    }
    return result
}