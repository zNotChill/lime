package me.znotchill.lime.commands

class Command(val name: String, val block: CommandTree.() -> Unit)

fun command(name: String, block: LiteralBuilder.() -> Unit): Command = Command(name) {
    val index = literal(name, block)
    addChildToRoot(index)
}