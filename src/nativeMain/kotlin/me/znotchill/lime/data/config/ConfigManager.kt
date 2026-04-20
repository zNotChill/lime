package me.znotchill.lime.data.config

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.znotchill.lime.data.DataHolder
import me.znotchill.lime.data.DataManager

object ConfigManager : DataHolder {
    override val loggerTag = "Config"
    override val id = "config"
    lateinit var server: LimeConfig

    override fun load() {
        val file = getFile("server.toml")
        if (!DataManager.fs.exists(file)) {
            log.i("Creating default server.toml...")
            server = LimeConfig(
                status = StatusConfig(),
                servers = ServersConfig(
                    mapOf(), listOf()
                )
            )
            save()
        } else {
            val content = DataManager.fs.read(file) { readUtf8() }
            server = DataManager.toml.decodeFromString<LimeConfig>(content)
        }
    }

    override fun save() {
        val file = getFile("server.toml")
        val encoded = DataManager.toml.encodeToString(server)
        DataManager.fs.write(file) { writeUtf8(encoded) }
    }
}