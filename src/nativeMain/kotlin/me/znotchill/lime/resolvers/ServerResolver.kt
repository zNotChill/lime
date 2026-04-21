package me.znotchill.lime.resolvers

import io.ktor.network.selector.SelectorManager
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.log.Loggable

interface ServerResolver : Loggable {
    suspend fun resolveServer(
        name: String,
        player: MinecraftPlayer?,
        selector: SelectorManager
    ): ResolverResponse
}