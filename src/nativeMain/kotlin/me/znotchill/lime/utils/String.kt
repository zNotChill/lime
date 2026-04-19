package me.znotchill.lime.utils

fun String.escapeJson() = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")