import io.ktor.network.sockets.Socket
import kotlinx.coroutines.CoroutineScope

enum class ConnectionState {
    HANDSHAKE, STATUS, LOGIN, PLAY
}

class MinecraftPlayer(
    val clientConnection: ClientConnection,
    var state: ConnectionState = ConnectionState.HANDSHAKE
) {
    var username: String? = null
    var uuid: String? = null
    
    var remoteConnection: ClientConnection? = null

    suspend fun disconnect(reason: String) {
        println("disconnecting $username: $reason")
        clientConnection.close()
        remoteConnection?.close()
    }
    
    suspend fun switchServer(newSocket: Socket, scope: CoroutineScope) {
        remoteConnection?.close()
        remoteConnection = ClientConnection(newSocket, scope)
    }
}