package me.znotchill.lime.components

import me.znotchill.lime.utils.escapeJson

sealed class ClickEvent {
    abstract fun toJson(): String

    data class OpenUrl(val url: String) : ClickEvent() {
        override fun toJson() = """{"action":"open_url","value":"${url.escapeJson()}"}"""
    }
    data class RunCommand(val command: String) : ClickEvent() {
        override fun toJson() = """{"action":"run_command","value":"${command.escapeJson()}"}"""
    }
    data class SuggestCommand(val command: String) : ClickEvent() {
        override fun toJson() = """{"action":"suggest_command","value":"${command.escapeJson()}"}"""
    }
    data class CopyToClipboard(val text: String) : ClickEvent() {
        override fun toJson() = """{"action":"copy_to_clipboard","value":"${text.escapeJson()}"}"""
    }
}

sealed class HoverEvent {
    abstract fun toJson(): String

    data class ShowText(val component: Component) : HoverEvent() {
        override fun toJson() = """{"action":"show_text","contents":${component.toJson()}}"""
    }
}