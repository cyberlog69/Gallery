package com.gallery.core.vault

import java.io.File
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise-grade, hardware-compatible AES-256-GCM vault encryption engine.
 * Ensures zero-leakage offline protection for sensitive photos and videos.
 */
object JvmVaultEngine {

    private const val ALGORITHM = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    /**
     * Derives a 256-bit AES SecretKey from a user passphrase and salt.
     */
    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Generates a cryptographically strong random salt.
     */
    fun generateSalt(length: Int = 16): ByteArray {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Output format: [12-byte IV] + [Ciphertext with 16-byte GCM Auth Tag]
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext)
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    /**
     * Decrypts ciphertext bytes encrypted with AES-256-GCM.
     * Expects input: [12-byte IV] + [Ciphertext with Auth Tag]
     */
    fun decrypt(encryptedPayload: ByteArray, key: SecretKey): ByteArray {
        require(encryptedPayload.size > GCM_IV_LENGTH_BYTES) {
            "Encrypted payload is too short to contain IV"
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedPayload, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val ciphertextOffset = GCM_IV_LENGTH_BYTES
        val ciphertextSize = encryptedPayload.size - GCM_IV_LENGTH_BYTES

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        return cipher.doFinal(encryptedPayload, ciphertextOffset, ciphertextSize)
    }

    /**
     * Encrypts a source file into a vault target file.
     */
    fun encryptFile(sourceFile: File, targetEncryptedFile: File, key: SecretKey) {
        val plainBytes = sourceFile.readBytes()
        val encryptedBytes = encrypt(plainBytes, key)
        targetEncryptedFile.writeBytes(encryptedBytes)
        wipeMemory(plainBytes)
    }

    /**
     * Decrypts a vault file directly into volatile memory (without writing plaintext to disk).
     */
    fun decryptToMemory(targetEncryptedFile: File, key: SecretKey): ByteArray {
        val encBytes = targetEncryptedFile.readBytes()
        return decrypt(encBytes, key)
    }

    /**
     * Securely zeroes out memory to prevent forensic buffer dumps.
     */
    fun wipeMemory(buffer: ByteArray) {
        Arrays.fill(buffer, 0.toByte())
    }
}
