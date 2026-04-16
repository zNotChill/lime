import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap.alloc
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import packets.PacketRegistry
import packets.registry.ChatPacket
import packets.registry.HandshakePacket
import kotlinx.cinterop.*
import platform.zlib.*

val json = Json {
    encodeDefaults = true
}

fun main() = runBlocking {
    val selectorManager = SelectorManager(Dispatchers.Default)
    val serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", 30067)

    val proxyScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    println("Native Proxy started on port 30067")

    PacketRegistry.register(0x00) { HandshakePacket.decode(it) }
    PacketRegistry.register(0x08) { ChatPacket.decode(it) }

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