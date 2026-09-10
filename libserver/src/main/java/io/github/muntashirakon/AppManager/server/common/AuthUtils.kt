// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AuthUtils {
    private val RANDOM = SecureRandom()

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(32)
        RANDOM.nextBytes(nonce)
        return nonce
    }

    @Throws(IOException::class)
    fun calculateHmac(token: String, data: ByteArray): ByteArray {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            return mac.doFinal(data)
        } catch (e: Exception) {
            throw IOException("Failed to calculate HMAC", e)
        }
    }
}
