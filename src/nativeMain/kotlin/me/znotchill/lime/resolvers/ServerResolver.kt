package me.znotchill.lime.resolvers

import io.ktor.network.selector.*
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.log.Loggable

interface ServerResolver : Loggable {
    suspend fun resolveServer(
        name: String,
        player: MinecraftPlayer?,
        selector: SelectorManager
    ): ResolverResponse

    suspend fun getTryList(
        player: MinecraftPlayer?,
        selector: SelectorManager
    ): List<String> {
        return listOf()
    }
}