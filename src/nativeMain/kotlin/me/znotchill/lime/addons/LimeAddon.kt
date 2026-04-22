package me.znotchill.lime.addons

import me.znotchill.lime.log.Loggable

open class LimeAddon(
    val name: String,
    val authors: List<String>? = null,
    val version: String? = null
) : Loggable {
    override val loggerTag = name
    internal var enabled: Boolean = false

    open suspend fun onEnable() {}
    open suspend fun onDisable() {}

    fun getAuthorsString(): String {
        return if (authors?.isNotEmpty() == true)
            authors.joinToString { ", " }
        else "???"
    }
}