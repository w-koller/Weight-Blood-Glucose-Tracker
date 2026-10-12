package io.github.wkoller.glucosetracker.data.prefs

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The plan PIN is a light lock against accidental changes, not strong security. It is still stored
 * only as a salted hash.
 */
object PinHasher {
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let(Base64.getEncoder()::encodeToString)

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(key)
    }

    fun matches(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }
}
