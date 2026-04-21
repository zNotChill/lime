package me.znotchill.lime.utils

fun Int.toHex(): String {
    val hex = toString(16)
    return if (hex.length == 1) "0$hex" else hex
}