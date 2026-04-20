package me.znotchill.lime.packets

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray

class RawPacket(val id: Int, val rawBytes: ByteArray) {
    val data: Source get() = Buffer().apply { write(rawBytes) }

    companion object {
        fun fromSource(id: Int, source: Source): RawPacket {
            return RawPacket(id, source.readByteArray())
        }
    }
}