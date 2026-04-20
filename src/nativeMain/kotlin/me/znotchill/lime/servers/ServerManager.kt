package me.znotchill.lime.servers

import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.exceptions.AlreadyExistsException
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.utils.toSocketAddress
import kotlin.collections.component1
import kotlin.collections.component2

object ServerManager : Loggable {
    override val loggerTag = "Server"
    private val servers: MutableList<Server> = mutableListOf()

    fun list(): List<Server> {
        return servers.toList()
    }

    fun get(name: String): Server? {
        return servers.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun register(server: Server) {
        if (get(server.name) != null)
            throw AlreadyExistsException("Server \"${server.name}\" is already registered!")

        log.i("Registered server \"${server.name}\" @ ${server.address}")
        servers.add(server)
    }

    fun registerAllFromConfig() {
        ConfigManager.server.servers.servers.forEach { (name, strAddress) ->
            val address = strAddress.toSocketAddress()
            register(
                Server(
                    name,
                    address
                )
            )
        }
    }
}