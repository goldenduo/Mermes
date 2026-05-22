package com.mermes.app.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.mermes.common.log.MermesLog
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Mermes 凭证强安全加解密工具
 * 内置 Android KeyStore 硬件级 AES-GCM 加密，并集成高健壮性混淆级 AES 降级方案以防闪退
 */
object CryptoUtil {
    private const val TAG = "CryptoUtil"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "MermesCredentialKey"

    // KeyStore AES/GCM 转换器
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    // 降级混淆 AES-CBC 密钥 (用于在 KeyStore 不可用或无锁屏硬件时自愈)
    private val OBF_SECRET_KEY = byteArrayOf(
        0x4d, 0x65, 0x72, 0x6d, 0x65, 0x73, 0x53, 0x65, // MermesSe
        0x63, 0x75, 0x72, 0x65, 0x53, 0x61, 0x6c, 0x74  // cureSalt
    ) // 16 字节 AES-128 密钥

    private const val PREFIX_KEYSTORE = "ENC:KST:"
    private const val PREFIX_OBFUSCATED = "ENC:OBF:"

    init {
        try {
            initKeyStoreKey()
        } catch (e: Exception) {
            MermesLog.e(TAG, "Failed to initialize Android KeyStore key, fallback will be used", e)
        }
    }

    /**
     * 初始化 Android KeyStore 中的 AES 密钥
     */
    private fun initKeyStoreKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
            MermesLog.i(TAG, "Android KeyStore AES key generated successfully.")
        }
    }

    /**
     * 获取 KeyStore 秘钥
     */
    private fun getKeyStoreSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            MermesLog.w(TAG, "Could not retrieve key from KeyStore", e)
            null
        }
    }

    /**
     * 加密字符串
     *
     * @param plainText 明文字符串
     * @return 经过 Base64 编码的密文字符串（带加密前缀）
     */
    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return plainText

        // 1. 尝试使用 KeyStore 硬件级加密
        try {
            val secretKey = getKeyStoreSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
                
                // 拼接 IV 和密文 [IV_Length (1 byte)] + [IV (12 bytes)] + [EncryptedData]
                val combined = ByteArray(1 + iv.size + encryptedBytes.size)
                combined[0] = iv.size.toByte()
                System.arraycopy(iv, 0, combined, 1, iv.size)
                System.arraycopy(encryptedBytes, 0, combined, 1 + iv.size, encryptedBytes.size)

                val base64Str = Base64.encodeToString(combined, Base64.NO_WRAP)
                return PREFIX_KEYSTORE + base64Str
            }
        } catch (e: Exception) {
            MermesLog.w(TAG, "KeyStore encryption failed, falling back to obfuscated encryption", e)
        }

        // 2. 降级自愈：使用内置混淆密钥进行标准 AES 加密
        return try {
            val keySpec = SecretKeySpec(OBF_SECRET_KEY, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val base64Str = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            PREFIX_OBFUSCATED + base64Str
        } catch (e: Exception) {
            MermesLog.e(TAG, "Obfuscated encryption also failed, returning raw string!", e)
            plainText
        }
    }

    /**
     * 解密字符串
     *
     * @param cipherText 密文字符串（若无加密前缀则直接返回原始串）
     * @return 解密后的明文字符串
     */
    fun decrypt(cipherText: String?): String? {
        if (cipherText.isNullOrEmpty()) return cipherText

        // 如果没有加密标记，说明是旧版未加密的明文配置，直接返回
        if (!cipherText.startsWith(PREFIX_KEYSTORE) && !cipherText.startsWith(PREFIX_OBFUSCATED)) {
            return cipherText
        }

        if (cipherText.startsWith(PREFIX_KEYSTORE)) {
            val base64Data = cipherText.substring(PREFIX_KEYSTORE.length)
            try {
                val combined = Base64.decode(base64Data, Base64.DEFAULT)
                val ivSize = combined[0].toInt()
                val iv = ByteArray(ivSize)
                System.arraycopy(combined, 1, iv, 0, ivSize)

                val encryptedBytes = ByteArray(combined.size - 1 - ivSize)
                System.arraycopy(combined, 1 + ivSize, encryptedBytes, 0, encryptedBytes.size)

                val secretKey = getKeyStoreSecretKey()
                if (secretKey != null) {
                    val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
                    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val decryptedBytes = cipher.doFinal(encryptedBytes)
                    return String(decryptedBytes, StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {
                MermesLog.e(TAG, "Failed to decrypt KeyStore payload, trying obfuscated fallback", e)
            }
        }

        // 如果是 OBF 前缀或是 KeyStore 解密失败的密文，尝试使用混淆解密
        val base64Obf = if (cipherText.startsWith(PREFIX_OBFUSCATED)) {
            cipherText.substring(PREFIX_OBFUSCATED.length)
        } else if (cipherText.startsWith(PREFIX_KEYSTORE)) {
            // 这是为了防止某些极其罕见的密钥丢失但使用了混淆垫底的场景
            cipherText.substring(PREFIX_KEYSTORE.length)
        } else {
            cipherText
        }

        return try {
            val keySpec = SecretKeySpec(OBF_SECRET_KEY, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.decode(base64Obf, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            MermesLog.e(TAG, "Decryption totally failed, returning encrypted data to avoid failure", e)
            cipherText
        }
    }
}
