
package me.znotchill.lime.components

sealed class TextColor(val value: String) {
    object Black : TextColor("black")
    object DarkBlue : TextColor("dark_blue")
    object DarkGreen : TextColor("dark_green")
    object DarkAqua : TextColor("dark_aqua")
    object DarkRed : TextColor("dark_red")
    object DarkPurple : TextColor("dark_purple")
    object Gold : TextColor("gold")
    object Gray : TextColor("gray")
    object DarkGray : TextColor("dark_gray")
    object Blue : TextColor("blue")
    object Green : TextColor("green")
    object Aqua : TextColor("aqua")
    object Red : TextColor("red")
    object LightPurple : TextColor("light_purple")
    object Yellow : TextColor("yellow")
    object White : TextColor("white")

    class Hex(hex: String) : TextColor(hex)

    companion object {
        fun hex(hex: String) = Hex(hex)
    }
}