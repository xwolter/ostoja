package pl.wolter.ostoja.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Szyfrowanie sejfu kluczem AES-256-GCM trzymanym w Android Keystore (na Samsungu - w Knox/TEE).
 * Klucz nigdy nie opuszcza sprzętu, więc plik sejfu nie da się odczytać poza tym telefonem.
 */
object KeystoreCipher {
    private const val ALIAS = "ostoja.vault.v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        return byteArrayOf(1, iv.size.toByte()) + iv + c.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 2 && blob[0] == 1.toByte()) { "Nieznany format sejfu" }
        val ivLen = blob[1].toInt()
        val iv = blob.copyOfRange(2, 2 + ivLen)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return c.doFinal(blob, 2 + ivLen, blob.size - 2 - ivLen)
    }
}

/** Kopia zapasowa chroniona hasłem: PBKDF2-SHA256 (310 tys. iteracji) + AES-256-GCM. */
object BackupCipher {
    private val MAGIC = "OSTOJA1".toByteArray()
    private const val ITER = 310_000

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = f.generateSecret(PBEKeySpec(password, salt, ITER, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    fun isBackup(blob: ByteArray) = blob.size > MAGIC.size + 28 && blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** Rzuca wyjątek przy złym haśle (błąd uwierzytelnienia GCM). */
    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        require(isBackup(blob)) { "To nie jest kopia Ostoi" }
        var p = MAGIC.size
        val salt = blob.copyOfRange(p, p + 16); p += 16
        val iv = blob.copyOfRange(p, p + 12); p += 12
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        return c.doFinal(blob, p, blob.size - p)
    }
}
