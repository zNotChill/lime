package me.znotchill.lime.components

import me.znotchill.lime.components.builders.ComponentBuilder
import me.znotchill.lime.components.builders.TextComponentBuilder

fun text(value: String, block: TextComponentBuilder.() -> Unit = {}): TextComponent =
    TextComponentBuilder(value).apply(block).build()

fun translatable(key: String) = Component.translatable(key)

fun component(block: ComponentBuilder.() -> Unit): TextComponent =
    ComponentBuilder().apply(block).build()