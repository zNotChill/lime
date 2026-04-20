package me.znotchill.lime

import co.touchlab.kermit.Logger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.connection.ClientConnection
import me.znotchill.lime.client.pipeline.SessionPipeline
import me.znotchill.lime.data.DataManager
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.events.DefaultEvents
import me.znotchill.lime.generated.Protocol
import me.znotchill.lime.log.LimeLogWriter
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.registry.clientbound.login.SetCompressionPacket
import me.znotchill.lime.packets.registry.clientbound.play.CommandsPacket
import me.znotchill.lime.packets.registry.clientbound.play.SystemChatPacket
import me.znotchill.lime.packets.registry.serverbound.configuration.FinishConfigurationPacket
import me.znotchill.lime.packets.registry.serverbound.handshake.HandshakePacket
import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.packets.registry.serverbound.play.ChatPacket
import me.znotchill.lime.packets.registry.serverbound.play.CommandPacket
import me.znotchill.lime.packets.registry.serverbound.play.TabCompleteRequestPacket
import me.znotchill.lime.packets.registry.serverbound.status.PingRequestPacket
import me.znotchill.lime.packets.registry.serverbound.status.StatusRequestPacket
import me.znotchill.lime.registries.PacketProtocolRegistry
import me.znotchill.lime.servers.ServerManager
import me.znotchill.lime.utils.bind
import me.znotchill.lime.utils.toSocketAddress
import kotlin.time.Clock

val json = Json {
    encodeDefaults = true
}

class LimeProxy : Loggable {
    override val loggerTag = "Main"
    companion object {
        val selectorManager = SelectorManager(Dispatchers.Default)
    }

    suspend fun start() = run {
        Logger.setLogWriters(LimeLogWriter())
        val start = Clock.System.now().toEpochMilliseconds()

        val lines = """
            [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [0m [0m [0m [0m [0m [0m [0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [0m [0m[38;2;75;105;47m▄[38;2;75;105;47m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;120;209;46m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;120;209;46m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;120;209;46m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;153;229;80m▀[0m[38;2;255;255;255m▄[0m [0m [0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [38;2;75;105;47m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;255;255;255m▀[38;2;120;209;46m[48;2;120;209;46m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;120;209;46m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;120;209;46m▀[38;2;255;255;255m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;120;209;46m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [0m [0m [0m [0m [0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [38;2;75;105;47m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;120;209;46m▀[38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;120;209;46m▀[38;2;255;255;255m[48;2;120;209;46m▀[38;2;120;209;46m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;120;209;46m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [0m[38;2;75;105;47m▀[38;2;75;105;47m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;75;105;47m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;120;209;46m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;120;209;46m▀[38;2;120;209;46m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;153;229;80m▀[38;2;120;209;46m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;75;105;47m▀[0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [0m [0m[38;2;75;105;47m▀[0m[38;2;75;105;47m▀[38;2;75;105;47m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;75;105;47m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;255;255;255m[48;2;120;209;46m▀[38;2;120;209;46m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;153;229;80m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;153;229;80m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;75;105;47m▀[0m [0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [38;2;255;255;255m[48;2;255;255;255m▀[38;2;255;255;255m[48;2;255;255;255m▀[0m [0m [0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m[38;2;255;255;255m▄[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m[38;2;75;105;47m▀[0m[38;2;75;105;47m▀[0m[38;2;75;105;47m▀[38;2;75;105;47m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;75;105;47m▀[38;2;255;255;255m[48;2;75;105;47m▀[0m[38;2;75;105;47m▀[0m [0m [0m [0m [0m [0m [0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m [0m [0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m [0m [0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m [0m [0m [0m [0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m[38;2;255;255;255m▀[0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
            [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m [0m
        """.trimIndent()
        lines.split("\n").forEach { println(it) }

        DataManager.register(ConfigManager)
        DataManager.initialize()

        log.i("Lime Proxy starting @ ${ConfigManager.server.status.bind}")

        ServerManager.registerAllFromConfig()

        val serverSocket = aSocket(selectorManager).tcp().bind(
            ConfigManager.server.status.bind.toSocketAddress()
        )

        val proxyScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        PacketProtocolRegistry.register(Protocol.protocol774)

        HandshakePacket.init()
        ChatPacket.init()
        CommandPacket.init()
        LoginStartPacket.init()
        FinishConfigurationPacket.init()
        SystemChatPacket.init()
        CommandsPacket.init()
        TabCompleteRequestPacket.init()
        StatusRequestPacket.init()
        PingRequestPacket.init()

        SetCompressionPacket.init()

        DefaultEvents.register()

        val end = Clock.System.now().toEpochMilliseconds()
        log.i("Lime Proxy started in ${end - start}ms @ ${ConfigManager.server.status.bind}")

        while (true) {
            val socket = serverSocket.accept()
            proxyScope.launch {
                val playerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                val connection = ClientConnection(socket, playerScope)
                val player = MinecraftPlayer(connection, playerScope)
                connection.player = player

                val pipeline = SessionPipeline(connection, playerScope, selectorManager)
                pipeline.player = player
                player.pipeline = pipeline

                player.pipeline.startClientReader()
                player.pipeline.startDispatcher(player)
            }
        }
    }
}

fun main() = runBlocking {
    val proxy = LimeProxy()
    proxy.start()
}