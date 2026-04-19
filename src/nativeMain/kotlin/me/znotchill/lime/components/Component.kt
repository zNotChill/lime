package me.znotchill.lime.components

import kotlinx.io.Sink
import me.znotchill.lime.nbt.NbtCompound
import me.znotchill.lime.nbt.NbtString
import me.znotchill.lime.nbt.nbtCompound
import me.znotchill.lime.utils.escapeJson

sealed class Component {
    abstract fun toJson(): String

    fun toNbt(): NbtCompound = when (this) {
        is TextComponent -> {
            val isPlain = color == null && bold == null && italic == null &&
                    underlined == null && strikethrough == null && obfuscated == null &&
                    clickEvent == null && hoverEvent == null && extra.isEmpty()

            if (isPlain) {
                nbtCompound { string("text", text) }
            } else {
                nbtCompound {
                    string("text", text)
                    color?.let { string("color", it.value) }
                    bold?.let { boolean("bold", it) }
                    italic?.let { boolean("italic", it) }
                    underlined?.let { boolean("underlined", it) }
                    strikethrough?.let { boolean("strikethrough", it) }
                    obfuscated?.let { boolean("obfuscated", it) }
                    if (extra.isNotEmpty()) {
                        compounds("extra", extra.map { it.toNbt() })
                    }
                }
            }
        }
        is TranslatableComponent -> nbtCompound {
            string("translate", key)
            color?.let { string("color", it.value) }
        }
    }

    fun writeNbt(output: Sink) {
        when {
            this is TextComponent && color == null && bold == null && italic == null &&
                    underlined == null && strikethrough == null && obfuscated == null &&
                    clickEvent == null && hoverEvent == null && extra.isEmpty() -> {
                NbtString(text).writeInline(output)
            }
            else -> toNbt().writeInline(output)
        }
    }

    companion object {
        fun text(value: String) = TextComponent(value)
        fun translatable(key: String) = TranslatableComponent(key)
    }
}

data class TextComponent(
    val text: String,
    val color: TextColor? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underlined: Boolean? = null,
    val strikethrough: Boolean? = null,
    val obfuscated: Boolean? = null,
    val clickEvent: ClickEvent? = null,
    val hoverEvent: HoverEvent? = null,
    val extra: List<Component> = emptyList()
) : Component() {
    override fun toJson(): String = buildString {
        append("""{"text":"${text.escapeJson()}"""")
        color?.let { append(""","color":"${it.value}"""") }
        bold?.let { append(""","bold":$it""") }
        italic?.let { append(""","italic":$it""") }
        underlined?.let { append(""","underlined":$it""") }
        strikethrough?.let { append(""","strikethrough":$it""") }
        obfuscated?.let { append(""","obfuscated":$it""") }
        clickEvent?.let { append(""","clickEvent":${it.toJson()}""") }
        hoverEvent?.let { append(""","hoverEvent":${it.toJson()}""") }
        if (extra.isNotEmpty()) {
            append(""","extra":[${extra.joinToString(",") { it.toJson() }}]""")
        }
        append("}")
    }

    fun color(color: TextColor) = copy(color = color)
    fun bold() = copy(bold = true)
    fun italic() = copy(italic = true)
    fun underlined() = copy(underlined = true)
    fun strikethrough() = copy(strikethrough = true)
    fun obfuscated() = copy(obfuscated = true)
    fun onClick(event: ClickEvent) = copy(clickEvent = event)
    fun onHover(event: HoverEvent) = copy(hoverEvent = event)
    fun append(vararg components: Component) = copy(extra = extra + components)
    operator fun plus(other: Component) = append(other)
}

data class TranslatableComponent(
    val key: String,
    val color: TextColor? = null,
    val with: List<Component> = emptyList()
) : Component() {
    override fun toJson(): String = buildString {
        append("""{"translate":"${key.escapeJson()}"""")
        color?.let { append(""","color":"${it.value}"""") }
        if (with.isNotEmpty()) {
            append(""","with":[${with.joinToString(",") { it.toJson() }}]""")
        }
        append("}")
    }
    fun with(vararg args: Component) = copy(with = with + args)
}