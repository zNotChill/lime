package me.znotchill.lime.events.packets

import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.events.EventPriority
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.MinecraftPacket
import kotlin.reflect.KClass

object PacketEventManager : Loggable {
    override val loggerTag = "Events"
    class RegisteredHandler<T : MinecraftPacket>(
        val priority: EventPriority,
        val handler: PacketHandler<T>
    )

    val handlers = mutableMapOf<KClass<*>, MutableList<RegisteredHandler<*>>>()

    inline fun <reified T : MinecraftPacket> register(
        priority: EventPriority = EventPriority.NORMAL,
        handler: PacketHandler<T>
    ) {
        val list = handlers.getOrPut(T::class) { mutableListOf() }
        list.add(RegisteredHandler(priority, handler))
        list.sortByDescending { it.priority.ordinal }
    }

    suspend fun <T : MinecraftPacket> emit(player: MinecraftPlayer, packet: T): Boolean {
        val packetHandlers = handlers[packet::class] ?: return false
        val context = PacketEventContext(player, packet)

        for (registered in packetHandlers) {
            @Suppress("UNCHECKED_CAST")
            val handler = registered.handler as PacketHandler<T>

            handler.handle(context)

            if (context.isCancelled) break
        }

        return context.isCancelled
    }
}