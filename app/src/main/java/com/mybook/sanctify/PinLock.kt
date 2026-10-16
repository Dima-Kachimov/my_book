package com.mybook.sanctify

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Хеширование PIN-кода и ответа на секретный вопрос (PBKDF2 с солью; в открытом виде не хранятся). */
object PinLock {
    private const val ITERATIONS = 20_000

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    fun hash(secret: String, salt: String): String {
        val spec = PBEKeySpec(secret.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun matches(secret: String, salt: String, expected: String): Boolean =
        MessageDigest.isEqual(hash(secret, salt).toByteArray(), expected.toByteArray())

    /** Ответ сравнивается без учёта регистра, лишних пробелов и буквы «ё». */
    fun normalizeAnswer(a: String): String = a.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")
}
