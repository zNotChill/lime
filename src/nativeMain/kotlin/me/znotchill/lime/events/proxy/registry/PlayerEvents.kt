package me.znotchill.lime.events.proxy.registry

import me.znotchill.lime.events.proxy.ProxyEvent
import me.znotchill.lime.servers.Server

class PlayerLoginEvent : ProxyEvent
class PlayerJoinEvent : ProxyEvent
class PlayerQuitEvent : ProxyEvent

data class PlayerServerSwitchStartEvent(
    val newServer: Server
) : ProxyEvent
data class PlayerServerSwitchFailedEvent(
    val newServer: Server?,
    val reason: String
) : ProxyEvent
data class PlayerServerSwitchEvent(
    val previousServer: Server,
    val newServer: Server
) : ProxyEvent

data class PlayerChatEvent(
    val message: String
) : ProxyEvent
data class PlayerCommandEvent(
    val command: String
) : ProxyEvent