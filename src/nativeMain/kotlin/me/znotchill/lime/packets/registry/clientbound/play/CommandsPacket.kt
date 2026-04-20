package me.znotchill.lime.packets.registry.clientbound.play

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readDouble
import kotlinx.io.readFloat
import kotlinx.io.writeDouble
import kotlinx.io.writeFloat
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.client.PipeDirection
import me.znotchill.lime.generated.Packet
import me.znotchill.lime.packets.ClientPacket
import me.znotchill.lime.packets.PacketRegistry
import me.znotchill.lime.packets.payloads.CommandNode
import me.znotchill.lime.packets.payloads.NodeType
import me.znotchill.lime.packets.readMcString
import me.znotchill.lime.packets.readVarInt
import me.znotchill.lime.packets.writeMcString
import me.znotchill.lime.packets.writeVarInt

data class CommandsPacket(
    val nodes: List<CommandNode> = listOf(),
    val rootIndex: Int = 0
) : ClientPacket {
    override val id = Packet.Clientbound.Play.DeclareCommands
    override val state = ConnectionState.PLAY

    companion object {
        fun init() = PacketRegistry.register(
            ConnectionState.PLAY,
            PipeDirection.CLIENT,
            Packet.Clientbound.Play.DeclareCommands,
            ::decode
        )

        fun decode(packet: Source): CommandsPacket {
            val nodeCount = packet.readVarInt()
            val nodes = mutableListOf<CommandNode>()

            repeat(nodeCount) {
                val flags = packet.readByte().toInt()
                val nodeType = NodeType.from(flags and 0x03)
                val executable = (flags and 0x04) != 0
                val hasRedirect = (flags and 0x08) != 0
                val hasSuggestions = (flags and 0x10) != 0

                val childCount = packet.readVarInt()
                val children = IntArray(childCount) { packet.readVarInt() }

                val redirectNode = if (hasRedirect) packet.readVarInt() else null

                val name = if (nodeType == NodeType.LITERAL || nodeType == NodeType.ARGUMENT) {
                    packet.readMcString()
                } else null

                val parserId: Int?
                val properties: ByteArray?

                if (nodeType == NodeType.ARGUMENT) {
                    parserId = packet.readVarInt()
                    properties = readParserProperties(packet, parserId)
                } else {
                    parserId = null
                    properties = null
                }

                val suggestionsType = if (hasSuggestions) packet.readMcString() else null

                nodes.add(CommandNode(
                    type = nodeType,
                    executable = executable,
                    children = children,
                    redirectNode = redirectNode,
                    name = name,
                    parserId = parserId,
                    properties = properties,
                    suggestionsType = suggestionsType
                ))
            }

            val rootIndex = packet.readVarInt()
            return CommandsPacket(nodes, rootIndex)
        }


        private fun readParserProperties(packet: Source, parserId: Int): ByteArray {
            val buf = kotlinx.io.Buffer()
            when (parserId) {
                0 -> { }

                // brigadier:float
                1 -> {
                    val flags = packet.readByte()
                    buf.writeByte(flags)
                    if (flags.toInt() and 0x01 != 0) buf.writeFloat(packet.readFloat())
                    if (flags.toInt() and 0x02 != 0) buf.writeFloat(packet.readFloat())
                }

                // brigadier:double
                2 -> {
                    val flags = packet.readByte()
                    buf.writeByte(flags)
                    if (flags.toInt() and 0x01 != 0) buf.writeDouble(packet.readDouble())
                    if (flags.toInt() and 0x02 != 0) buf.writeDouble(packet.readDouble())
                }

                // brigadier:integer
                3 -> {
                    val flags = packet.readByte()
                    buf.writeByte(flags)
                    if (flags.toInt() and 0x01 != 0) buf.writeInt(packet.readInt())
                    if (flags.toInt() and 0x02 != 0) buf.writeInt(packet.readInt())
                }

                // brigadier:long
                4 -> {
                    val flags = packet.readByte()
                    buf.writeByte(flags)
                    if (flags.toInt() and 0x01 != 0) buf.writeLong(packet.readLong())
                    if (flags.toInt() and 0x02 != 0) buf.writeLong(packet.readLong())
                }

                // brigadier:string
                5 -> {
                    val stringType = packet.readVarInt()
                    writeVarIntToBuffer(buf, stringType)
                }

                // minecraft:entity
                6 -> buf.writeByte(packet.readByte())

                // minecraft:score_holder
                12 -> buf.writeByte(packet.readByte())

                // minecraft:range
                41 -> buf.writeByte(packet.readByte())

                // minecraft:resource, minecraft:resource_or_tag,
                // minecraft:resource_or_tag_key, minecraft:resource_key
                in listOf(45, 46, 47, 48) -> {
                    val id = packet.readMcString()
                    val idBytes = id.encodeToByteArray()
                    writeVarIntToBuffer(buf, idBytes.size)
                    buf.write(idBytes)
                }

                else -> { }
            }
            return buf.readByteArray()
        }

        private fun writeVarIntToBuffer(buf: kotlinx.io.Buffer, value: Int) {
            var v = value
            while (true) {
                if ((v and 0x7F.inv()) == 0) { buf.writeByte(v.toByte()); return }
                buf.writeByte((v and 0x7F or 0x80).toByte())
                v = v ushr 7
            }
        }
    }

    override fun encode(output: Sink) {
        output.writeVarInt(nodes.size)

        for (node in nodes) {
            output.writeByte(node.buildFlags())

            output.writeVarInt(node.children.size)
            for (child in node.children) output.writeVarInt(child)

            node.redirectNode?.let { output.writeVarInt(it) }

            if (node.type == NodeType.LITERAL || node.type == NodeType.ARGUMENT) {
                output.writeMcString(node.name ?: error("Non root node missing name"))
            }

            if (node.type == NodeType.ARGUMENT) {
                output.writeVarInt(node.parserId ?: error("Argument node missing parserId"))
                node.properties?.let { output.write(it) }
            }

            node.suggestionsType?.let { output.writeMcString(it) }
        }

        output.writeVarInt(rootIndex)
    }
}