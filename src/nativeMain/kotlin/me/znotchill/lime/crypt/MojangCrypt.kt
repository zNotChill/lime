package me.znotchill.lime.crypt

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.znotchill.lime.packets.toNotchianHex

object MojangCrypt {
    private val provider = CryptographyProvider.Default

    suspend fun generateKeyPair(): RSA.PKCS1.KeyPair {
        val rsa = provider.get(RSA.PKCS1)
        return rsa.keyPairGenerator(2048.bits).generateKey()
    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun digestData(
        serverId: String,
        publicKey: RSA.PKCS1.PublicKey,
        secretKey: AES.Key,
    ): String {
        val digest = provider.get(SHA1).hasher()
        val publicKeyBytes = publicKey.encodeToByteArray(RSA.PublicKey.Format.DER)
        val secretKeyBytes = secretKey.encodeToByteArray(AES.Key.Format.RAW)
        return digest.hash(
            serverId.toByteArray(Charsets.ISO_8859_1) +
                    secretKeyBytes +
                    publicKeyBytes
        ).toNotchianHex()
    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun decryptUsingKey(
        privateKey: RSA.PKCS1.PrivateKey,
        bytes: ByteArray,
    ): ByteArray = withContext(Dispatchers.IO) {
        privateKey.decryptor().decrypt(bytes)
    }

    suspend fun decryptByteToSecretKey(
        privateKey: RSA.PKCS1.PrivateKey,
        bytes: ByteArray,
    ): AES.Key {
        val raw = decryptUsingKey(privateKey, bytes)
        return provider.get(AES.CBC).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, raw)
    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun encrypt(key: AES.CFB8.Key, data: ByteArray): ByteArray {
        val cipher = key.cipher()
        val iv = key.encodeToByteArray(AES.Key.Format.RAW)
        return cipher.encryptWithIv(iv, data)
    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun decrypt(key: AES.CFB8.Key, data: ByteArray): ByteArray {
        val cipher = key.cipher()
        val iv = key.encodeToByteArray(AES.Key.Format.RAW)
        return cipher.decryptWithIv(iv, data)
    }

    suspend fun hmacSign(secret: ByteArray, data: ByteArray): ByteArray {
        val hmac = provider.get(HMAC)
        val key = hmac.keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, secret)
        return key.signatureGenerator().generateSignature(data)
    }
}