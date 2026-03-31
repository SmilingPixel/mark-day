package io.github.smiling_pixel.util

import kotlin.random.Random

/**
 * Generates a UUID v4 string used as the stable cross-device synchronization identifier.
 */
fun generateSyncId(): String {
    val bytes = ByteArray(16)
    Random.nextBytes(bytes)

    // Set version (4) and variant (RFC 4122).
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

    val hex = bytes.joinToString("") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
}
