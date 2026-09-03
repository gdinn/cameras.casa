package com.gdisys.cameras.core.storage.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

interface CryptoEngine {
  fun encrypt(bytes: ByteArray): ByteArray
  fun decrypt(bytes: ByteArray): ByteArray
}

object Crypto : CryptoEngine {
  private const val STORAGE_KEY_ALIAS = "gdi-sys-storage"
  private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
  private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
  private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
  private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"

  private val keyStore = KeyStore
    .getInstance("AndroidKeyStore")
    .apply {
      load(null)
    }

  private fun getKey(): SecretKey {
    val existingKey = keyStore
      .getEntry(STORAGE_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
    return existingKey?.secretKey ?: createKey()
  }

  private fun createKey(): SecretKey {
    val keyGenerator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
    keyGenerator.init(
      KeyGenParameterSpec.Builder(
        STORAGE_KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(BLOCK_MODE)
        .setEncryptionPaddings(PADDING)
        .setRandomizedEncryptionRequired(true)
        .setUserAuthenticationRequired(false)
        .build()
    )
    return keyGenerator.generateKey()
  }

  private fun getCipher(): Cipher = Cipher.getInstance(TRANSFORMATION)

  override fun encrypt(bytes: ByteArray): ByteArray {
    val cipher = getCipher()
    cipher.init(Cipher.ENCRYPT_MODE, getKey())
    val iv = cipher.iv
    val encrypted = cipher.doFinal(bytes)
    return iv + encrypted
  }

  override fun decrypt(bytes: ByteArray): ByteArray {
    val cipher = getCipher()
    val ivSize = 16 // AES block size
    if (bytes.size < ivSize) throw IllegalArgumentException("Invalid data for decryption")

    val iv = bytes.copyOfRange(0, ivSize)
    val data = bytes.copyOfRange(ivSize, bytes.size)
    cipher.init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
    return cipher.doFinal(data)
  }
}