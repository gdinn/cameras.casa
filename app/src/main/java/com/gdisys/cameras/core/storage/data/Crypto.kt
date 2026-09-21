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

/**
 * Cifra AES/CBC/PKCS7 com a chave guardada no Android Keystore sob [alias].
 *
 * Cada storage instancia o seu próprio engine com um alias distinto, de modo que a perda ou a
 * invalidação da chave de um storage não afeta os demais.
 */
class KeystoreCryptoEngine(private val alias: String) : CryptoEngine {

  private val keyStore = KeyStore
    .getInstance("AndroidKeyStore")
    .apply {
      load(null)
    }

  private fun getKey(): SecretKey {
    val existingKey = keyStore
      .getEntry(alias, null) as? KeyStore.SecretKeyEntry
    return existingKey?.secretKey ?: createKey()
  }

  private fun createKey(): SecretKey {
    val keyGenerator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
    keyGenerator.init(
      KeyGenParameterSpec.Builder(
        alias,
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

  private companion object {
    const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
  }
}
