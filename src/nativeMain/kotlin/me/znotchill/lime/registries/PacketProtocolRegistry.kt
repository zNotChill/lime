package me.znotchill.lime.registries

import me.znotchill.lime.client.ConnectionState
import kotlinx.serialization.Serializable
import me.znotchill.lime.client.PipeDirection

object PacketProtocolRegistry {
    private val idToName = mutableMapOf<Long, String>()
    private val nameToId = mutableMapOf<String, Int>()

    private fun getInternalKey(version: Int, state: ConnectionState, direction: PipeDirection, id: Int): Long {
        return (version.toLong() shl 32) or (direction.value.toLong() shl 16) or (state.ordinal.toLong() shl 8) or id.toLong()
    }

    private fun getNameKey(version: Int, state: ConnectionState, direction: PipeDirection, name: String): String {
        return "${version}_${direction.value}_${state.ordinal}_$name"
    }

    fun register(protocol: ProtocolPackets) {
        val v = protocol.version

        fun mapList(state: ConnectionState, direction: PipeDirection, list: List<String>) {
            list.forEachIndexed { id, name ->
                nameToId[getNameKey(v, state, direction, name)] = id
            }
        }

        mapList(ConnectionState.HANDSHAKE, PipeDirection.SERVER, protocol.serverbound.handshake)
        mapList(ConnectionState.STATUS, PipeDirection.SERVER, protocol.serverbound.status)
        mapList(ConnectionState.LOGIN, PipeDirection.SERVER, protocol.serverbound.login)
        mapList(ConnectionState.CONFIGURATION, PipeDirection.SERVER, protocol.serverbound.config)
        mapList(ConnectionState.PLAY, PipeDirection.SERVER, protocol.serverbound.play)

        mapList(ConnectionState.STATUS, PipeDirection.CLIENT, protocol.clientbound.status)
        mapList(ConnectionState.LOGIN, PipeDirection.CLIENT, protocol.clientbound.login)
        mapList(ConnectionState.CONFIGURATION, PipeDirection.CLIENT, protocol.clientbound.config)
        mapList(ConnectionState.PLAY, PipeDirection.CLIENT, protocol.clientbound.play)
    }

    fun getName(version: Int, state: ConnectionState, direction: PipeDirection, id: Int): String? {
        return idToName[getInternalKey(version, state, direction, id)]
    }

    fun getId(version: Int, state: ConnectionState, direction: PipeDirection, name: String): Int? {
        return nameToId[getNameKey(version, state, direction, name)]
    }
}

@Serializable
data class ProtocolPackets(
    val version: Int,
    val serverbound: ServerProtocolPackets,
    val clientbound: ClientProtocolPackets,
)

@Serializable
data class ServerProtocolPackets(
    val handshake: List<String>,
    val status: List<String>,
    val login: List<String>,
    val config: List<String>,
    val play: List<String>,
)

@Serializable
data class ClientProtocolPackets(
    val status: List<String>,
    val login: List<String>,
    val config: List<String>,
    val play: List<String>,
)

