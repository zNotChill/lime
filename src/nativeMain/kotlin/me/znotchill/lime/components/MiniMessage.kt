import me.znotchill.lime.components.Color
import me.znotchill.lime.components.Component
import me.znotchill.lime.components.TextColor
import me.znotchill.lime.components.TextComponent

object MiniMessage {
    fun parse(input: String): Component {
        val tokens = tokenize(input)
        val styleStack = ArrayDeque<StyleState>()

        // add base style w/ no formatting
        styleStack.addLast(StyleState())

        val components = mutableListOf<Component>()

        for (token in tokens) {
            when (token) {
                is Token.Text -> {
                    // apply the current style to the text node & collect
                    val style = styleStack.last()
                    components.add(style.apply(TextComponent(token.content)))
                }
                is Token.Tag -> {
                    if (token.closing) {
                        // pop the style added by the matching opening tag
                        if (styleStack.size > 1) styleStack.removeLast()
                    } else {
                        // inherit a new style from teh current one and push it
                        styleStack.addLast(styleStack.last().withTag(token))
                    }
                }
            }
        }

        // flatten
        return when {
            components.isEmpty() -> TextComponent("")
            components.size == 1 -> components.first()
            else -> components.drop(1).fold(components.first() as TextComponent) { acc, comp ->
                acc.append(comp)
            }
        }
    }

    private sealed class Token {
        data class Text(val content: String) : Token()
        data class Tag(val name: String, val value: String?, val closing: Boolean) : Token()
    }

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            if (input[i] == '<') {
                val end = input.indexOf('>', i)

                // no closing >, it's probably plaintext and should not be parsed as a tag
                if (end == -1) {
                    sb.append(input[i++])
                    continue
                }
                if (sb.isNotEmpty()) {
                    tokens.add(Token.Text(sb.toString()))
                    sb.clear()
                }

                val tagContent = input.substring(i + 1, end)
                val closing = tagContent.startsWith("/")
                val inner = if (closing) tagContent.drop(1) else tagContent

                // split "key:value"
                // "color:#99e550" -> name="color", value="#99e550"
                val colonIdx = inner.indexOf(':')
                val name = if (colonIdx == -1) inner else inner.take(colonIdx)
                val value = if (colonIdx == -1) null else inner.substring(colonIdx + 1)
                tokens.add(Token.Tag(name.lowercase(), value, closing))
                i = end + 1
            } else {
                sb.append(input[i++])
            }
        }
        if (sb.isNotEmpty()) tokens.add(Token.Text(sb.toString()))
        return tokens
    }

    private data class StyleState(
        val color: Color? = null,
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underlined: Boolean? = null,
        val strikethrough: Boolean? = null,
        val obfuscated: Boolean? = null
    ) {
        fun apply(c: TextComponent) = c
            .let { if (color != null) it.color(color) else it }
            .let { if (bold == true) it.bold() else it }
            .let { if (italic == true) it.italic() else it }
            .let { if (underlined == true) it.underlined() else it }
            .let { if (strikethrough == true) it.strikethrough() else it }
            .let { if (obfuscated == true) it.obfuscated() else it }

        // apply styles
        fun withTag(tag: Token.Tag) = when (tag.name) {
            "bold", "b" -> copy(bold = true)
            "italic", "i", "em" -> copy(italic = true)
            "underlined", "u" -> copy(underlined = true)
            "strikethrough", "st", "s" -> copy(strikethrough = true)
            "obfuscated", "obf" -> copy(obfuscated = true)
            "color", "colour", "c" -> copy(color = tag.value?.let { Color.fromString(it) })
            "black" -> copy(color = TextColor.Black)
            "dark_blue" -> copy(color = TextColor.DarkBlue)
            "dark_green" -> copy(color = TextColor.DarkGreen)
            "dark_aqua" -> copy(color = TextColor.DarkAqua)
            "dark_red" -> copy(color = TextColor.DarkRed)
            "dark_purple" -> copy(color = TextColor.DarkPurple)
            "gold" -> copy(color = TextColor.Gold)
            "gray" -> copy(color = TextColor.Gray)
            "dark_gray" -> copy(color = TextColor.DarkGray)
            "blue" -> copy(color = TextColor.Blue)
            "green" -> copy(color = TextColor.Green)
            "aqua" -> copy(color = TextColor.Aqua)
            "red" -> copy(color = TextColor.Red)
            "light_purple" -> copy(color = TextColor.LightPurple)
            "yellow" -> copy(color = TextColor.Yellow)
            "white" -> copy(color = TextColor.White)
            else -> {
                // bare hex tag without a hex: prefix
                val hex = tag.name.takeIf { it.startsWith("#") }
                if (hex != null) copy(color = Color.Hex(hex)) else this
            }
        }
    }
}