package me.znotchill.lime.utils

data class SocketAddress(
    val host: String,
    val port: Int
)

object NetworkUtils {
    fun parseAddress(input: String, defaultPort: Int = 25565): SocketAddress {
        val trimmed = input.trim()
        
        // ipv6
        if (trimmed.startsWith("[")) {
            val bracketEnd = trimmed.lastIndexOf("]")
            if (bracketEnd != -1) {
                val host = trimmed.substring(1, bracketEnd)
                val portPart = trimmed.substring(bracketEnd + 1)
                val port = if (portPart.startsWith(":") && portPart.length > 1) {
                    portPart.substring(1).toIntOrNull() ?: defaultPort
                } else {
                    defaultPort
                }
                return SocketAddress(host, port)
            }
        }

        // ipv4
        val lastColon = trimmed.lastIndexOf(":")
        
        return if (lastColon != -1 && lastColon == trimmed.indexOf(":")) {
            // only 1 colon so it's ipv4
            val host = trimmed.take(lastColon)
            val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: defaultPort
            SocketAddress(host.ifEmpty { "0.0.0.0" }, port)
        } else if (lastColon != -1) {
            // ipv6 no port
            SocketAddress(trimmed, defaultPort)
        } else {
            SocketAddress(trimmed.ifEmpty { "0.0.0.0" }, defaultPort)
        }
    }
}

fun String.toSocketAddress(): SocketAddress {
    return NetworkUtils.parseAddress(this)
}