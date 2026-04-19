package me.znotchill.lime.commands

import me.znotchill.lime.packets.payloads.CommandNode
import me.znotchill.lime.packets.payloads.NodeType

typealias ExecuteHandler = suspend (CommandContext) -> Unit
typealias SuggestHandler = suspend (SuggestionContext) -> List<String>

class CommandTree {
    internal val nodes = mutableListOf<CommandNode>()
    internal val executeHandlers = mutableMapOf<String, ExecuteHandler>()
    internal val suggestHandlers = mutableMapOf<String, SuggestHandler>()

    init {
        nodes.add(CommandNode(type = NodeType.ROOT, executable = false, children = intArrayOf()))
    }

    fun literal(name: String, block: LiteralBuilder.() -> Unit = {}): Int {
        val builder = LiteralBuilder(name, this, name)
        builder.block()
        return builder.build()
    }

    internal fun addNode(node: CommandNode): Int {
        val index = nodes.size
        nodes.add(node)
        return index
    }

    internal fun addChildToRoot(childIndex: Int) {
        val root = nodes[0]
        nodes[0] = root.copy(children = root.children + childIndex)
    }
}

class LiteralBuilder(
    private val name: String,
    private val tree: CommandTree,
    private val path: String
) {
    private var executable = false
    private val childIndices = mutableListOf<Int>()

    fun executes(handler: ExecuteHandler): LiteralBuilder {
        executable = true
        tree.executeHandlers[path] = handler
        return this
    }

    fun literal(name: String, block: LiteralBuilder.() -> Unit = {}): LiteralBuilder {
        val builder = LiteralBuilder(name, tree, "$path.$name")
        builder.block()
        childIndices.add(builder.build())
        return this
    }

    fun argument(name: String, block: ArgumentBuilder.() -> Unit = {}): LiteralBuilder {
        val builder = ArgumentBuilder(name, tree, "$path.<$name>")
        builder.block()
        childIndices.add(builder.build())
        return this
    }

    internal fun build(): Int {
        return tree.addNode(
            CommandNode(
                type = NodeType.LITERAL,
                executable = executable,
                children = childIndices.toIntArray(),
                name = name
            )
        )
    }
}

class ArgumentBuilder(
    private val name: String,
    private val tree: CommandTree,
    private val path: String
) {
    private var executable = false
    private var parserId: Int = 5
    private var properties: ByteArray? = byteArrayOf(0)
    private var suggestionsType: String? = null
    private val childIndices = mutableListOf<Int>()

    fun executes(handler: ExecuteHandler): ArgumentBuilder {
        executable = true
        tree.executeHandlers[path] = handler
        return this
    }

    fun suggests(type: SuggestionType, handler: SuggestHandler? = null): ArgumentBuilder {
        suggestionsType = type.id
        handler?.let { tree.suggestHandlers[path] = it }
        return this
    }

    fun parser(id: Int, props: ByteArray? = null): ArgumentBuilder {
        parserId = id
        properties = props
        return this
    }

    fun literal(name: String, block: LiteralBuilder.() -> Unit = {}): ArgumentBuilder {
        val builder = LiteralBuilder(name, tree, "$path.$name")
        builder.block()
        childIndices.add(builder.build())
        return this
    }

    fun argument(name: String, block: ArgumentBuilder.() -> Unit = {}): ArgumentBuilder {
        val builder = ArgumentBuilder(name, tree, "$path.<$name>")
        builder.block()
        childIndices.add(builder.build())
        return this
    }

    internal fun build(): Int {
        return tree.addNode(
            CommandNode(
                type = NodeType.ARGUMENT,
                executable = executable,
                children = childIndices.toIntArray(),
                name = name,
                parserId = parserId,
                properties = properties,
                suggestionsType = suggestionsType
            )
        )
    }
}

enum class SuggestionType(val id: String) {
    ASK_SERVER("minecraft:ask_server"),
    ALL_RECIPES("minecraft:all_recipes"),
    AVAILABLE_SOUNDS("minecraft:available_sounds"),
    SUMMONABLE_ENTITIES("minecraft:summonable_entities")
}