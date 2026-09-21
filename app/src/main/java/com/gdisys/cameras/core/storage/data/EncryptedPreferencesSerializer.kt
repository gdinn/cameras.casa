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
 * DataStore serializer that writes the [T] model as JSON, encrypted with [crypto] and Base64
 * encoded. Any read failure (bad Base64, failed decryption, malformed JSON) falls back to
 * [defaultValue], so a corrupt store does not take the app down with it.
 *
 * That fallback is also why [json] is configured rather than default: silently returning the
 * default value means silently discarding the user's settings, so the encoding is tuned to make
 * decoding fail as rarely as possible. See [json].
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
      json.decodeFromString(serializer, decodedJsonString)
    } catch (e: Exception) {
      e.printStackTrace()
      // If decryption fails (e.g. BadPaddingException), return default value to avoid crash
      defaultValue
    }
  }

  override suspend fun writeTo(t: T, output: OutputStream) {
    val encodedJson = json.encodeToString(serializer, t)
    val bytes = encodedJson.toByteArray()
    val encryptedBytes = crypto.encrypt(bytes)
    val encryptedBytesBase64 = Base64.getEncoder().encode(encryptedBytes)
    withContext(Dispatchers.IO) {
      output.use {
        it.write(encryptedBytesBase64)
      }
    }
  }

  internal companion object {
    /**
     * JSON format shared by every encrypted storage.
     *
     * `encodeDefaults` writes properties that still hold their default value, so the file is
     * self-describing and a field such as a schema version actually reaches disk instead of being
     * skipped for matching its default.
     *
     * `ignoreUnknownKeys` lets an older build read a file written by a newer one: an unknown field
     * is dropped instead of failing the whole decode, which here would mean wiping the user's
     * settings.
     */
    internal val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
  }
}
