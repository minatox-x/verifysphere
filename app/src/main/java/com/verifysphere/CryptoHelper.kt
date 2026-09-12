package com.verifysphere

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-CBC encryption helper.
 *
 * The key is split across multiple obfuscated methods so a simple
 * string search or grep on the DEX cannot surface it in one place.
 * ProGuard's repackaging + overload-aggression further obfuscates.
 *
 * Wire format: Base64( IV[16 bytes] || CipherText )
 */
internal object CryptoHelper {

    // Key is split into 4 parts and never stored as a single literal.
    // With ProGuard -overloadaggressively + -repackageclasses '',
    // these method names become single-char symbols in the release APK.
    private fun p1(): ByteArray = byteArrayOf(0x56, 0x65, 0x72, 0x69, 0x66, 0x79, 0x53, 0x70)
    private fun p2(): ByteArray = byteArrayOf(0x68, 0x65, 0x72, 0x65, 0x4B, 0x65, 0x79, 0x31)
    private fun p3(): ByteArray = byteArrayOf(0x41, 0x45, 0x53, 0x32, 0x35, 0x36, 0x42, 0x69)
    private fun p4(): ByteArray = byteArrayOf(0x74, 0x53, 0x65, 0x63, 0x72, 0x65, 0x74, 0x21)

    private fun buildKey(): SecretKeySpec {
        val raw = p1() + p2() + p3() + p4()  // 32 bytes → AES-256
        return SecretKeySpec(raw, "AES")
    }

    /**
     * Decrypt a Base64-encoded ciphertext (IV prepended).
     * Returns the plaintext string, or throws on failure.
     */
    fun decrypt(base64Input: String): String {
        val raw = Base64.decode(base64Input.trim(), Base64.DEFAULT)
        require(raw.size > 16) { "Payload too short" }

        val iv = raw.copyOfRange(0, 16)
        val ct = raw.copyOfRange(16, raw.size)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, buildKey(), IvParameterSpec(iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    /**
     * Encrypt a plaintext string.
     * Returns Base64( IV[16] || CipherText ).
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, buildKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }
}
