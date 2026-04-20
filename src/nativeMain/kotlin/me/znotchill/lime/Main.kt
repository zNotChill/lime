package me.znotchill.lime

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import me.znotchill.lime.data.DataManager
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.events.DefaultEvents
import me.znotchill.lime.generated.Protocol
import me.znotchill.lime.packets.registry.clientbound.login.SetCompressionPacket
import me.znotchill.lime.packets.registry.clientbound.play.CommandsPacket
import me.znotchill.lime.packets.registry.clientbound.play.SystemChatPacket
import me.znotchill.lime.packets.registry.serverbound.configuration.FinishConfigurationPacket
import me.znotchill.lime.packets.registry.serverbound.handshake.HandshakePacket
import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.packets.registry.serverbound.play.ChatPacket
import me.znotchill.lime.packets.registry.serverbound.play.CommandPacket
import me.znotchill.lime.packets.registry.serverbound.play.TabCompleteRequestPacket
import me.znotchill.lime.registries.PacketProtocolRegistry
import me.znotchill.lime.utils.NetworkUtils
import me.znotchill.lime.utils.bind
import me.znotchill.lime.utils.toSocketAddress

val json = Json {
    encodeDefaults = true
}

fun main() = runBlocking {
    DataManager.register(ConfigManager)
    DataManager.initialize()

    val selectorManager = SelectorManager(Dispatchers.Default)

    val serverSocket = aSocket(selectorManager).tcp().bind(
        ConfigManager.server.status.bind.toSocketAddress()
    )

    val proxyScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    PacketProtocolRegistry.register(Protocol.protocol774)

    println("Native Proxy started @ ${ConfigManager.server.status.bind}")

    HandshakePacket.init()
    ChatPacket.init()
    CommandPacket.init()
    LoginStartPacket.init()
    FinishConfigurationPacket.init()
    SystemChatPacket.init()
    CommandsPacket.init()
    TabCompleteRequestPacket.init()

    SetCompressionPacket.init()

    DefaultEvents.register()

    while (true) {
        val socket = serverSocket.accept()
        launch {
            val connection = ClientConnection(socket, proxyScope)
            val player = MinecraftPlayer(connection)

            connection.handlePlayerSession(player, selectorManager)
        }
    }
}