package me.znotchill.lime.packets.payloads

enum class NodeType(val id: Int) {
    ROOT(0), LITERAL(1), ARGUMENT(2);
    companion object { fun from(id: Int) = entries.first { it.id == id } }
}

data class CommandNode(
    val type: NodeType,
    val executable: Boolean,
    val children: IntArray,
    val redirectNode: Int? = null,
    val name: String? = null,
    val parserId: Int? = null,
    val properties: ByteArray? = null,
    val suggestionsType: String? = null
) {
    fun buildFlags(): Byte {
        var flags = type.id and 0x03
        if (executable)          flags = flags or 0x04
        if (redirectNode != null) flags = flags or 0x08
        if (suggestionsType != null) flags = flags or 0x10
        return flags.toByte()
    }
}