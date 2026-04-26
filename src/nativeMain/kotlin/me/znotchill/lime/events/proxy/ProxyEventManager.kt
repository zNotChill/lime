package me.znotchill.lime.events.proxy

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.events.EventPriority
import me.znotchill.lime.log.Loggable
import kotlin.reflect.KClass

object ProxyEventManager : Loggable {
    override val loggerTag = "Events"
    class RegisteredHandler<T : ProxyEvent>(
        val priority: EventPriority,
        val handler: ProxyEventHandler<T>
    )

    val handlers = mutableMapOf<KClass<*>, MutableList<RegisteredHandler<*>>>()

    inline fun <reified T : ProxyEvent> register(
        priority: EventPriority = EventPriority.NORMAL,
        handler: ProxyEventHandler<T>
    ) {
        val list = handlers.getOrPut(T::class) { mutableListOf() }
        list.add(RegisteredHandler(priority, handler))
        list.sortByDescending { it.priority.ordinal }
    }

    fun <T : ProxyEvent> emit(player: MinecraftPlayer, event: T): Boolean {
        val eventHandlers = handlers[event::class] ?: return false
        val context = ProxyEventContext(player, event)

        for (registered in eventHandlers) {
            @Suppress("UNCHECKED_CAST")
            val handler = registered.handler as ProxyEventHandler<T>

            try {
                handler.handle(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (context.isCancelled) break
        }

        return context.isCancelled
    }
}