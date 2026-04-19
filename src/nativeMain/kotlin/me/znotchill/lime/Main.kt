package me.znotchill.lime

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
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
import platform.zlib.Z_OK
import platform.zlib.compress
import platform.zlib.uncompress

val json = Json {
    encodeDefaults = true
}

fun main() = runBlocking {
    val selectorManager = SelectorManager(Dispatchers.Default)
    val serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", 30067)

    val proxyScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    PacketProtocolRegistry.register(Protocol.protocol774)

    println("Native Proxy started on port 30067")

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


@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
fun decompressZlib(compressedData: ByteArray, uncompressedSize: Int): ByteArray {
    val result = ByteArray(uncompressedSize)

    return memScoped {
        val scope = this

        compressedData.usePinned { pinnedCompressed ->
            result.usePinned { pinnedResult ->
                val destLen = scope.alloc<UIntVar>()
                destLen.value = uncompressedSize.toUInt()

                val status = uncompress(
                    pinnedResult.addressOf(0).reinterpret(),
                    destLen.ptr.reinterpret(),
                    pinnedCompressed.addressOf(0).reinterpret(),
                    compressedData.size.convert()
                )

                if (status != Z_OK) {
                    throw RuntimeException("Zlib decompression failed with status: $status")
                }
                result
            }
        }
    }
}
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
fun compressZlib(data: ByteArray): ByteArray {
    val maxCompressedSize = (data.size * 1.1).toInt() + 12
    val resultBuffer = ByteArray(maxCompressedSize)

    return data.usePinned { pinnedData ->
        resultBuffer.usePinned { pinnedResult ->
            memScoped {
                val destLen = alloc<ULongVar>()
                destLen.value = maxCompressedSize.toULong()

                val status = compress(
                    pinnedResult.addressOf(0).reinterpret(),
                    destLen.ptr.reinterpret(),
                    pinnedData.addressOf(0).reinterpret(),
                    data.size.toUInt()
                )

                if (status != Z_OK) throw RuntimeException("Compression failed")

                resultBuffer.copyOf(destLen.value.toInt())
            }
        }
    }
}