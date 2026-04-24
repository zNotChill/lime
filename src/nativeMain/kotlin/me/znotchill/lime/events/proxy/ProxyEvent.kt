package me.znotchill.lime.events.proxy

import me.znotchill.lime.client.MinecraftPlayer

interface ProxyEvent

fun interface ProxyEventHandler<T : ProxyEvent> {
    fun handle(context: ProxyEventContext<T>)
}

class ProxyEventContext<T : ProxyEvent>(
    val player: MinecraftPlayer,
    val event: T
) {
    var isCancelled: Boolean = false

    fun cancel() {
        isCancelled = true
    }
}