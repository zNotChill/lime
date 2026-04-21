package me.znotchill.lime.components.builders

import me.znotchill.lime.components.Component
import me.znotchill.lime.components.TextComponent

class ComponentBuilder {
    private val components = mutableListOf<Component>()

    fun text(value: String, block: TextComponentBuilder.() -> Unit = {}) {
        components.add(TextComponentBuilder(value).apply(block).build())
    }

    fun translatable(key: String) {
        components.add(Component.translatable(key))
    }

    fun space() {
        components.add(TextComponent(" "))
    }

    fun build(): TextComponent {
        val first = components.firstOrNull() ?: TextComponent("")
        val rest = components.drop(1)
        return if (first is TextComponent) first.append(*rest.toTypedArray())
        else TextComponent("").append(*components.toTypedArray())
    }
}