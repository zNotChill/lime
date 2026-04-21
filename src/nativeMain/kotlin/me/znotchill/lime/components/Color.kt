package me.znotchill.lime.components

import me.znotchill.lime.utils.toHex
import kotlin.math.abs

sealed class Color {
    abstract val value: String

    data class Hex(val hex: String) : Color() {
        override val value: String = hex.let {
            if (it.startsWith("#")) it else "#$it"
        }
    }

    data class Rgb(val r: Int, val g: Int, val b: Int) : Color() {
        override val value: String = buildString {
            append('#')
            append(r.toHex())
            append(g.toHex())
            append(b.toHex())
        }
    }

    data class Hsl(val h: Float, val s: Float, val l: Float) : Color() {
        override val value: String = toRgb().value

        private fun toRgb(): Rgb {
            val c = (1f - abs(2f * l - 1f)) * s
            val x = c * (1f - abs((h / 60f) % 2f - 1f))
            val m = l - c / 2f
            val (r, g, b) = when {
                h < 60  -> Triple(c, x, 0f)
                h < 120 -> Triple(x, c, 0f)
                h < 180 -> Triple(0f, c, x)
                h < 240 -> Triple(0f, x, c)
                h < 300 -> Triple(x, 0f, c)
                else    -> Triple(c, 0f, x)
            }
            return Rgb(
                ((r + m) * 255).toInt(),
                ((g + m) * 255).toInt(),
                ((b + m) * 255).toInt()
            )
        }
    }

    class Named internal constructor(override val value: String) : Color()

    companion object {
        fun hex(hex: String) = Hex(hex)
        fun rgb(r: Int, g: Int, b: Int) = Rgb(r, g, b)
        fun hsl(h: Float, s: Float, l: Float) = Hsl(h, s, l)
        fun fromString(value: String) =
            if (value.startsWith("#")) Hex(value) else Named(value)
    }
}