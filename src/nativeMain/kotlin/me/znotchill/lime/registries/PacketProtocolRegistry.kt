package me.znotchill.lime.registries

import me.znotchill.lime.ConnectionState
import kotlinx.serialization.Serializable

object PacketProtocolRegistry {
    private val idToName = mutableMapOf<Long, String>()
    private val nameToId = mutableMapOf<String, Int>()

    private fun getInternalKey(version: Int, state: ConnectionState, direction: Int, id: Int): Long {
        return (version.toLong() shl 32) or (direction.toLong() shl 16) or (state.ordinal.toLong() shl 8) or id.toLong()
    }

    private fun getNameKey(version: Int, state: ConnectionState, direction: Int, name: String): String {
        return "${version}_${direction}_${state.ordinal}_$name"
    }

    fun register(protocol: ProtocolPackets) {
        val v = protocol.version

        fun mapList(state: ConnectionState, direction: Int, list: List<String>) {
            list.forEachIndexed { id, name ->
                nameToId[getNameKey(v, state, direction, name)] = id
            }
        }

        // 0 = CLIENTBOUND
        // 1 = SERVERBOUND

        mapList(ConnectionState.HANDSHAKE, 1, protocol.serverbound.handshake)
        mapList(ConnectionState.STATUS, 1, protocol.serverbound.status)
        mapList(ConnectionState.LOGIN, 1, protocol.serverbound.login)
        mapList(ConnectionState.CONFIGURATION, 1, protocol.serverbound.config)
        mapList(ConnectionState.PLAY, 1, protocol.serverbound.play)

        mapList(ConnectionState.STATUS, 0, protocol.clientbound.status)
        mapList(ConnectionState.LOGIN, 0, protocol.clientbound.login)
        mapList(ConnectionState.CONFIGURATION, 0, protocol.clientbound.config)
        mapList(ConnectionState.PLAY, 0, protocol.clientbound.play)
    }

    fun getName(version: Int, state: ConnectionState, direction: Int, id: Int): String? {
        return idToName[getInternalKey(version, state, direction, id)]
    }

    fun getId(version: Int, state: ConnectionState, direction: Int, name: String): Int? {
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

