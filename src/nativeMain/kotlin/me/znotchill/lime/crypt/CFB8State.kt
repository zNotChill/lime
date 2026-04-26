package me.znotchill.lime.crypt

class CFB8State(key: ByteArray, iv: ByteArray) {
    private val roundKeys = AESLookup.expandKey(key)
    private val shiftRegister = iv.copyOf(16)
    private val keystreamBlock = ByteArray(16)

    fun encryptByte(b: Byte): Byte {
        AESLookup.encryptBlock(roundKeys, shiftRegister, keystreamBlock)
        val encrypted = (b.toInt() xor keystreamBlock[0].toInt()).toByte()
        shiftRegister.copyInto(shiftRegister, 0, 1, 16)
        shiftRegister[15] = encrypted
        return encrypted
    }

    fun decryptByte(b: Byte): Byte {
        AESLookup.encryptBlock(roundKeys, shiftRegister, keystreamBlock)
        val decrypted = (b.toInt() xor keystreamBlock[0].toInt()).toByte()
        shiftRegister.copyInto(shiftRegister, 0, 1, 16)
        shiftRegister[15] = b
        return decrypted
    }

    fun encrypt(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices) result[i] = encryptByte(data[i])
        return result
    }

    fun decrypt(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices) result[i] = decryptByte(data[i])
        return result
    }
}