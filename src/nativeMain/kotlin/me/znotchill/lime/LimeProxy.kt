package me.znotchill.lime

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import dev.whyoleg.cryptography.algorithms.RSA
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import me.znotchill.lime.addons.LimeAddon
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.client.connection.ClientConnection
import me.znotchill.lime.client.pipeline.SessionPipeline
import me.znotchill.lime.crypt.MojangCrypt
import me.znotchill.lime.data.DataManager
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.events.DefaultEvents
import me.znotchill.lime.exceptions.AlreadyExistsException
import me.znotchill.lime.generated.Protocol
import me.znotchill.lime.log.LimeLogWriter
import me.znotchill.lime.packets.registry.clientbound.login.LoginPluginRequestPacket
import me.znotchill.lime.packets.registry.clientbound.login.SetCompressionPacket
import me.znotchill.lime.packets.registry.clientbound.play.CommandsPacket
import me.znotchill.lime.packets.registry.clientbound.play.SystemChatPacket
import me.znotchill.lime.packets.registry.serverbound.configuration.FinishConfigurationPacket
import me.znotchill.lime.packets.registry.serverbound.handshake.HandshakePacket
import me.znotchill.lime.packets.registry.serverbound.login.EncryptionResponsePacket
import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.packets.registry.serverbound.play.ChatPacket
import me.znotchill.lime.packets.registry.serverbound.play.CommandPacket
import me.znotchill.lime.packets.registry.serverbound.play.TabCompleteRequestPacket
import me.znotchill.lime.packets.registry.serverbound.status.PingRequestPacket
import me.znotchill.lime.packets.registry.serverbound.status.StatusRequestPacket
import me.znotchill.lime.registries.PacketProtocolRegistry
import me.znotchill.lime.resolvers.LimeServerResolver
import me.znotchill.lime.resolvers.ServerResolver
import me.znotchill.lime.servers.ServerManager
import me.znotchill.lime.utils.SocketAddress
import me.znotchill.lime.utils.bind
import me.znotchill.lime.utils.toSocketAddress
import kotlin.system.exitProcess
import kotlin.time.Clock

val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

open class LimeProxy : LimeAddon(
    "Lime",
    authors = listOf("zNotChill")
) {
    companion object {
        lateinit var instance: LimeProxy
            private set

        val selectorManager = SelectorManager(Dispatchers.Default)

        var defaultResolver: ServerResolver = LimeServerResolver
            set(value) {
                instance.log.i("Default Resolver overridden to $value!")
                field = value
            }
        var logWriter: LogWriter = LimeLogWriter()

        var brand: String = "Lime"

        lateinit var keypair: RSA.PKCS1.KeyPair
    }

    val proxyScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val addons: MutableList<LimeAddon> = mutableListOf()

    fun registerAddon(addon: LimeAddon) {
        val existing = addons.firstOrNull {
            it.name.lowercase() == addon.name
        }

        if (existing != null)
            throw AlreadyExistsException("Addon \"${existing.name}\" by ${existing.getAuthorsString()} is already registered")

        addons.add(addon)
    }

    fun printDefaultHeader() {
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
    }

    fun loadDataManager() {
        DataManager.register(ConfigManager)
        DataManager.initialize()
    }

    suspend fun openServerSocket(
        address: SocketAddress = ConfigManager.server.status.bind.toSocketAddress()
    ) = aSocket(selectorManager).tcp().bind(
        address
    )

    suspend fun init() {
        instance = this
        start()
    }

    open suspend fun preLoad() {}
    open suspend fun postLoad() {}

    suspend fun start() = run {
        keypair = MojangCrypt.generateKeyPair()

        Logger.setLogWriters(logWriter)
        printDefaultHeader()
        val start = Clock.System.now().toEpochMilliseconds()

        onEnable()
        preLoad()
        loadDataManager()

        log.i("$brand Proxy starting @ ${ConfigManager.server.status.bind}")

        addons.forEach { addon ->
            try {
                log.i("Enabling addon \"${addon.name}\" by ${addon.getAuthorsString()}")
                addon.enabled = true
                addon.onEnable()
            } catch (e: Exception) {
                addon.enabled = false
                addon.onDisable()
                log.e("Disabling \"${addon.name}\": ${e.message}")
                e.printStackTrace()
            }
        }

        ServerManager.registerAllFromConfig()

        val address = ConfigManager.server.status.bind.toSocketAddress()
        val serverSocket: ServerSocket? = try {
            openServerSocket(address)
        } catch (e: PosixException.PosixErrnoException) {
            if (e.message?.contains("10013") == true) {
                log.e("Proxy cannot open on port ${address.port}! Port is in use.")
            } else {
                e.printStackTrace()
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (serverSocket == null) {
            exitProcess(1)
        }

        PacketProtocolRegistry.register(Protocol.protocol774)
        PacketProtocolRegistry.register(Protocol.protocol773)

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
        EncryptionResponsePacket.init()
        LoginPluginRequestPacket.init()

        SetCompressionPacket.init()

        DefaultEvents.register()

        val end = Clock.System.now().toEpochMilliseconds()
        log.i("$brand Proxy started in ${end - start}ms @ ${ConfigManager.server.status.bind}")

        postLoad()
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