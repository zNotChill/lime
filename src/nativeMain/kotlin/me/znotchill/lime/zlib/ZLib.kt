package me.znotchill.lime.zlib

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.zlib.Z_OK
import platform.zlib.compress
import platform.zlib.uncompress

object ZLib {
    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    fun decompress(compressedData: ByteArray, uncompressedSize: Int): ByteArray {
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
    fun compress(data: ByteArray): ByteArray {
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
}