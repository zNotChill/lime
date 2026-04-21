package me.znotchill.lime.components.builders

import me.znotchill.lime.components.ClickEvent
import me.znotchill.lime.components.Color
import me.znotchill.lime.components.Component
import me.znotchill.lime.components.HoverEvent
import me.znotchill.lime.components.TextComponent

class TextComponentBuilder(private val text: String) {
    var color: Color? = null
    var bold: Boolean? = null
    var italic: Boolean? = null
    var underlined: Boolean? = null
    var strikethrough: Boolean? = null
    var obfuscated: Boolean? = null
    var clickEvent: ClickEvent? = null
    var hoverEvent: HoverEvent? = null
    private val extra = mutableListOf<Component>()

    fun append(vararg components: Component) { extra.addAll(components) }
    operator fun Component.unaryPlus() { extra.add(this) }

    fun build() = TextComponent(
        text = text,
        color = color,
        bold = bold,
        italic = italic,
        underlined = underlined,
        strikethrough = strikethrough,
        obfuscated = obfuscated,
        clickEvent = clickEvent,
        hoverEvent = hoverEvent,
        extra = extra.toList()
    )
}