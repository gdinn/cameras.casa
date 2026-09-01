package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.Serializer
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

object UserPreferencesSerializer: Serializer<UserPreferences> {
  override val defaultValue: UserPreferences
    get() = UserPreferences()

  override suspend fun readFrom(input: InputStream): UserPreferences {
    val encryptedBytes = withContext(Dispatchers.IO) {
      input.use { it.readBytes() }
    }

    if (encryptedBytes.isEmpty()) {
      return defaultValue
    }

    return try {
      val encryptedBytesDecoded = Base64.getDecoder().decode(encryptedBytes)
      val decryptedBytes = Crypto.decrypt(encryptedBytesDecoded)
      val decodedJsonString = decryptedBytes.decodeToString()
      Json.decodeFromString(decodedJsonString)
    } catch (e: Exception) {
      e.printStackTrace()
      // If decryption fails (e.g. BadPaddingException), return default value to avoid crash
      defaultValue
    }
  }

  override suspend fun writeTo(t: UserPreferences, output: OutputStream) {
    val json = Json.encodeToString(t)
    val bytes = json.toByteArray()
    val encryptedBytes = Crypto.encrypt(bytes)
    val encryptedBytesBase64 = Base64.getEncoder().encode(encryptedBytes)
    withContext(Dispatchers.IO) {
      output.use {
        it.write(encryptedBytesBase64)
      }
    }
  }
}