package me.znotchill.lime.components

import kotlinx.io.Sink
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.znotchill.lime.nbt.NbtCompound
import me.znotchill.lime.nbt.NbtString
import me.znotchill.lime.nbt.nbtCompound
import me.znotchill.lime.utils.escapeJson

@Serializable(with = ComponentSerializer::class)
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

    abstract fun toPlainText(): String

    companion object {
        fun text(value: String) = TextComponent(value)
        fun translatable(key: String) = TranslatableComponent(key)

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun fromJson(json: String): Component {
            val element = jsonParser.parseToJsonElement(json)
            return fromJsonElement(element)
        }

        fun fromJsonElement(element: JsonElement): Component {
            if (element is JsonPrimitive && element.isString) {
                return TextComponent(element.content)
            }

            val obj = element.jsonObject
            return when {
                "text" in obj -> {
                    TextComponent(
                        text = obj["text"]?.jsonPrimitive?.content ?: "",
                        color = obj["color"]?.jsonPrimitive?.content?.let { Color.fromString(it) },
                        bold = obj["bold"]?.jsonPrimitive?.booleanOrNull,
                        italic = obj["italic"]?.jsonPrimitive?.booleanOrNull,
                        underlined = obj["underlined"]?.jsonPrimitive?.booleanOrNull,
                        strikethrough = obj["strikethrough"]?.jsonPrimitive?.booleanOrNull,
                        obfuscated = obj["obfuscated"]?.jsonPrimitive?.booleanOrNull,
                        extra = obj["extra"]?.jsonArray?.map { fromJsonElement(it) } ?: emptyList()
                    )
                }
                "translate" in obj -> {
                    TranslatableComponent(
                        key = obj["translate"]?.jsonPrimitive?.content ?: "",
                        color = obj["color"]?.jsonPrimitive?.content?.let { Color.fromString(it) },
                        with = obj["with"]?.jsonArray?.map { fromJsonElement(it) } ?: emptyList()
                    )
                }
                else -> TextComponent("")
            }
        }
    }
}

data class TextComponent(
    val text: String,
    val color: Color? = null,
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

    fun color(color: Color) = copy(color = color)
    fun bold() = copy(bold = true)
    fun italic() = copy(italic = true)
    fun underlined() = copy(underlined = true)
    fun strikethrough() = copy(strikethrough = true)
    fun obfuscated() = copy(obfuscated = true)
    fun onClick(event: ClickEvent) = copy(clickEvent = event)
    fun onHover(event: HoverEvent) = copy(hoverEvent = event)
    fun append(vararg components: Component) = copy(extra = extra + components)
    operator fun plus(other: Component) = append(other)

    override fun toPlainText(): String = buildString {
        append(text)
        extra.forEach { append(it.toPlainText()) }
    }
}

data class TranslatableComponent(
    val key: String,
    val color: Color? = null,
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

    override fun toPlainText(): String = buildString {
        append(key)
        if (with.isNotEmpty()) {
            append("[")
            append(with.joinToString(", ") { it.toPlainText() })
            append("]")
        }
    }
}