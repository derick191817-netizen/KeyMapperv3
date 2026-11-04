package com.example.keymapper.injection

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.flyfishxu.kadb.cert.KadbPrivateKeyStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persiste a identidade ADB do host criptografada por uma chave não exportável do Android Keystore.
 * O arquivo fica em noBackupFilesDir: não é migrado para outro aparelho por backup.
 */
class AndroidKadbPrivateKeyStore(private val directory: File) : KadbPrivateKeyStore {
    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "com.example.keymapper.adb.host.identity.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }

    private val lock = Any()
    private val keyFile = File(directory, "adb-host-identity.enc")

    override fun readPrivateKeyPem(): ByteArray? = synchronized(lock) {
        if (!keyFile.exists()) return@synchronized null
        val encrypted = keyFile.readBytes()
        if (encrypted.size <= IV_LENGTH_BYTES + 16) {
            throw IOException("O arquivo da identidade ADB está truncado.")
        }
        val iv = encrypted.copyOfRange(0, IV_LENGTH_BYTES)
        val payload = encrypted.copyOfRange(IV_LENGTH_BYTES, encrypted.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(payload)
    }

    override fun writePrivateKeyPemAtomic(privateKeyPem: ByteArray) {
        synchronized(lock) {
            directory.mkdirs()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv ?: throw IOException("O Android Keystore não forneceu IV para AES-GCM.")
            require(iv.size == IV_LENGTH_BYTES) { "Tamanho de IV AES-GCM inesperado." }
            val payload = iv + cipher.doFinal(privateKeyPem)
            val temporary = File(directory, "${keyFile.name}.tmp-${System.nanoTime()}")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(payload)
                    output.fd.sync()
                }
                try {
                    Files.move(
                        temporary.toPath(), keyFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), keyFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                if (temporary.exists()) temporary.delete()
                payload.fill(0)
            }
        }
    }

    override fun clear() = synchronized(lock) {
        if (keyFile.exists() && !keyFile.delete()) {
            throw IOException("Não foi possível apagar a identidade ADB criptografada.")
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
