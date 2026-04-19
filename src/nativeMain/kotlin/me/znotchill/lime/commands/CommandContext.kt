package me.znotchill.lime.commands

import me.znotchill.lime.MinecraftPlayer

class CommandContext(
    val player: MinecraftPlayer,
    val label: String,
    val args: List<String>
) {
    fun arg(index: Int): String = args.getOrElse(index) { "" }
    fun argOrNull(index: Int): String? = args.getOrNull(index)
}

class SuggestionContext(
    val player: MinecraftPlayer,
    val input: String,
    val allArgs: List<String>
)