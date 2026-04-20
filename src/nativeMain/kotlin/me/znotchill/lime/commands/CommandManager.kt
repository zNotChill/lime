package me.znotchill.lime.commands

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.packets.payloads.CommandNode
import me.znotchill.lime.packets.payloads.NodeType
import me.znotchill.lime.packets.registry.clientbound.play.CommandsPacket

object CommandManager {
    private val commands = mutableListOf<Pair<Command, CommandTree>>()

    fun exists(label: String): Boolean =
        commands.any { (cmd, _) -> cmd.name == label }

    fun register(command: Command) {
        val tree = CommandTree()
        tree.command(command)
        commands.add(command to tree)
    }

    fun buildPacket(existingNodes: List<CommandNode> = emptyList()): CommandsPacket {
        val mergedNodes = mutableListOf<CommandNode>()
        val rootChildren = mutableListOf<Int>()

        val existingRootIndex = if (existingNodes.isNotEmpty()) {
            existingNodes.indexOfFirst { it.type == NodeType.ROOT }.takeIf { it >= 0 } ?: 0
        } else 0

        if (existingNodes.isNotEmpty()) {
            mergedNodes.addAll(existingNodes)
            rootChildren.addAll(existingNodes[existingRootIndex].children.toList())
        } else {
            mergedNodes.add(CommandNode(type = NodeType.ROOT, executable = false, children = intArrayOf()))
        }

        for ((_, tree) in commands) {
            val offset = mergedNodes.size - 1
            for (i in 1 until tree.nodes.size) {
                val node = tree.nodes[i]
                mergedNodes.add(
                    node.copy(children = node.children.map { c -> c + offset }.toIntArray())
                )
            }
            tree.nodes[0].children.forEach { rootChildren.add(it + offset) }
        }

        mergedNodes[existingRootIndex] = mergedNodes[existingRootIndex].copy(
            children = rootChildren.toIntArray()
        )

        return CommandsPacket(nodes = mergedNodes, rootIndex = existingRootIndex)
    }

    suspend fun dispatch(player: MinecraftPlayer, input: String): Boolean {
        val parts = input.trimStart('/').split(" ")
        val label = parts[0]
        val args = parts.drop(1)

        for ((cmd, tree) in commands) {
            if (cmd.name != label) continue
            val path = resolveExecutePath(tree, label, args) ?: continue
            val handler = tree.executeHandlers[path] ?: continue
            handler(CommandContext(player, label, args))
            return true
        }
        return false
    }

    suspend fun suggest(player: MinecraftPlayer, input: String): List<String> {
        val parts = input.trimStart('/').split(" ")
        val label = parts[0]
        val args = parts.drop(1)
        val current = args.lastOrNull() ?: ""

        for ((cmd, tree) in commands) {
            if (cmd.name != label) continue
            val path = resolveSuggestPath(tree, label, args) ?: continue
            val handler = tree.suggestHandlers[path] ?: continue
            return handler(SuggestionContext(player, current, args))
                .filter { it.startsWith(current, ignoreCase = true) }
        }
        return emptyList()
    }

    private fun resolveExecutePath(tree: CommandTree, label: String, args: List<String>): String? {
        var path = label
        for (arg in args) {
            val literalPath = "$path.$arg"
            val argPath = "$path.<${argumentNameAt(tree, path)}>"
            path = when {
                tree.executeHandlers.containsKey(literalPath) || 
                tree.nodes.any { it.name == arg } -> literalPath
                else -> argPath
            }
        }
        return if (tree.executeHandlers.containsKey(path)) path else null
    }

    private fun resolveSuggestPath(tree: CommandTree, label: String, args: List<String>): String? {
        var path = label
        val argsWithoutLast = args.dropLast(1)
        for (arg in argsWithoutLast) {
            val literalPath = "$path.$arg"
            path = if (tree.nodes.any { it.name == arg }) literalPath
                   else "$path.<${argumentNameAt(tree, path)}>"
        }
        val argName = argumentNameAt(tree, path)
        return if (argName != null) "$path.<$argName>" else null
    }

    private fun argumentNameAt(tree: CommandTree, path: String): String? {
        return tree.suggestHandlers.keys
            .firstOrNull { it.startsWith("$path.<") }
            ?.removePrefix("$path.<")?.removeSuffix(">")
    }

    private fun CommandTree.command(cmd: Command) {
        cmd.block(this)
    }
}