package me.znotchill.lime.utils

class MinecraftUUID(val mostSignificantBits: Long, val leastSignificantBits: Long) {

    override fun toString(): String {
        return buildString {
            append(digits(mostSignificantBits shr 32, 8))
            append("-")
            append(digits(mostSignificantBits shr 16, 4))
            append("-")
            append(digits(mostSignificantBits, 4))
            append("-")
            append(digits(leastSignificantBits shr 48, 4))
            append("-")
            append(digits(leastSignificantBits, 12))
        }
    }

    private fun digits(val64: Long, digits: Int): String {
        val hi = 1L shl (digits * 4)
        return (hi or (val64 and (hi - 1))).toString(16).substring(1)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MinecraftUUID) return false
        return mostSignificantBits == other.mostSignificantBits && 
               leastSignificantBits == other.leastSignificantBits
    }

    override fun hashCode(): Int {
        var result = mostSignificantBits.hashCode()
        result = 31 * result + leastSignificantBits.hashCode()
        return result
    }

    companion object {
        fun fromString(name: String): MinecraftUUID {
            val components = name.split("-")
            if (components.size != 5)
                throw IllegalArgumentException("Invalid UUID: $name")

            var mostSigBits = components[0].toLong(16) shl 32
            mostSigBits = mostSigBits or (components[1].toLong(16) shl 16)
            mostSigBits = mostSigBits or components[2].toLong(16)

            var leastSigBits = components[3].toLong(16) shl 48
            leastSigBits = leastSigBits or components[4].toLong(16)

            return MinecraftUUID(mostSigBits, leastSigBits)
        }
    }
}