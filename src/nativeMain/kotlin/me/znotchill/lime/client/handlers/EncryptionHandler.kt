package me.znotchill.lime.client.handlers

import dev.whyoleg.cryptography.DelicateCryptographyApi
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import me.znotchill.lime.LimeProxy
import me.znotchill.lime.client.MinecraftPlayer
import me.znotchill.lime.crypt.MojangCrypt
import me.znotchill.lime.httpClient
import me.znotchill.lime.json
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.RawPacket
import me.znotchill.lime.packets.registry.serverbound.login.EncryptionResponsePacket

@Serializable
data class MojangProfile(
    val id: String,
    val name: String,
    val properties: List<MojangProperty>
)

@Serializable
data class MojangProperty(
    val name: String,
    val value: String,
    val signature: String? = null
)

object EncryptionHandler : Loggable {
    override val loggerTag = "Encryption"
    /**
     * Called when the client sends an EncryptionResponse to the proxy
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun handleClientResponse(player: MinecraftPlayer, rawPacket: RawPacket) {
        val packet = EncryptionResponsePacket.decode(rawPacket.data.peek())

        val decryptedNonce = MojangCrypt.decryptUsingKey(
            LimeProxy.keypair.privateKey,
            packet.verifyToken.toByteArray()
        )
        if (!decryptedNonce.contentEquals(player.pipeline.encryptionNonce!!)) {
            throw IllegalStateException("Nonce mismatch")
        }

        val secretKeyBytes = MojangCrypt.decryptUsingKey(
            LimeProxy.keypair.privateKey,
            packet.sharedSecret.toByteArray()
        )
        player.clientConnection.enableEncryption(secretKeyBytes)
        log.d("Enabled encryption")

        val serverHash = MojangCrypt.digestData(
            serverId = "",
            publicKey = LimeProxy.keypair.publicKey,
            secretKeyBytes = secretKeyBytes
        )

        val joinResponse = httpClient.get("https://sessionserver.mojang.com/session/minecraft/hasJoined") {
            parameter("username", player.username)
            parameter("serverId", serverHash)
        }

        val body = joinResponse.bodyAsText()
        val profile = json.decodeFromString<MojangProfile>(body)
        player.properties = profile.properties

        log.d("Authenticated ${player.username} through Mojang! (${profile.id})")

        player.pipeline.awaitingEncryptionResponse = false

        LoginHandler.completeLogin(player)
    }

    /**
     * Called when the backend server sends an EncryptionRequest to the client
     */
    suspend fun handleBackendRequest(player: MinecraftPlayer, rawPacket: RawPacket) {
        log.d("Backend sent encryption request. this should not happen with online-mode=false")
        log.d("Raw packet hex: ${rawPacket.data.peek().readByteArray().joinToString(" ") { it.toInt().and(0xFF).toString(16).padStart(2, '0') }}")
    }
}