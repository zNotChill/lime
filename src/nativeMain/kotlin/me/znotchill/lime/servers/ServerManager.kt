package me.znotchill.lime.servers

import me.znotchill.lime.exceptions.AlreadyExistsException

object ServerManager {
    private val servers: MutableList<Server> = mutableListOf()

    fun get(name: String): Server? {
        return servers.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun register(server: Server) {
        if (get(server.name) != null)
            throw AlreadyExistsException("Server \"${server.name}\" is already registered!")

        servers.add(server)
    }
}