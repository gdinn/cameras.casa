package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/**
 * Serializer de DataStore que grava o modelo [T] como JSON cifrado por [crypto] e codificado em
 * Base64. Qualquer falha de leitura (Base64 inválido, decriptação, JSON inválido) cai no
 * [defaultValue], para que um storage corrompido não derrube o app.
 */
class EncryptedPreferencesSerializer<T>(
  private val serializer: KSerializer<T>,
  override val defaultValue: T,
  private val crypto: CryptoEngine
) : Serializer<T> {

  override suspend fun readFrom(input: InputStream): T {
    val encryptedBytes = withContext(Dispatchers.IO) {
      input.use { it.readBytes() }
    }

    if (encryptedBytes.isEmpty()) {
      return defaultValue
    }

    return try {
      val encryptedBytesDecoded = Base64.getDecoder().decode(encryptedBytes)
      val decryptedBytes = crypto.decrypt(encryptedBytesDecoded)
      val decodedJsonString = decryptedBytes.decodeToString()
      Json.decodeFromString(serializer, decodedJsonString)
    } catch (e: Exception) {
      e.printStackTrace()
      // If decryption fails (e.g. BadPaddingException), return default value to avoid crash
      defaultValue
    }
  }

  override suspend fun writeTo(t: T, output: OutputStream) {
    val json = Json.encodeToString(serializer, t)
    val bytes = json.toByteArray()
    val encryptedBytes = crypto.encrypt(bytes)
    val encryptedBytesBase64 = Base64.getEncoder().encode(encryptedBytes)
    withContext(Dispatchers.IO) {
      output.use {
        it.write(encryptedBytesBase64)
      }
    }
  }
}
