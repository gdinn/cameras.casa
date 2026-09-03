package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

class UserPreferencesSerializerTest {

  private val crypto = mockk<CryptoEngine>()
  private val serializer = UserPreferencesSerializer(crypto)

  @Test
  fun `defaultValue is an empty UserPreferences`() {
    assertEquals(UserPreferences(), serializer.defaultValue)
  }

  @Test
  fun `readFrom an empty stream returns the default value without touching crypto`() = runTest {
    val result = serializer.readFrom(ByteArrayInputStream(ByteArray(0)))

    assertEquals(UserPreferences(), result)
    verify(exactly = 0) { crypto.decrypt(any()) }
  }

  @Test
  fun `readFrom decrypts and decodes the stored preferences`() = runTest {
    val preferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))
    val plainBytes = Json.encodeToString(preferences).toByteArray()
    val cipherBytes = "cipher".toByteArray()
    val storedBytes = Base64.getEncoder().encode(cipherBytes)
    every { crypto.decrypt(cipherBytes) } returns plainBytes

    val result = serializer.readFrom(ByteArrayInputStream(storedBytes))

    assertEquals(preferences, result)
    verify(exactly = 1) { crypto.decrypt(cipherBytes) }
  }

  @Test
  fun `readFrom falls back to the default value when decryption fails`() = runTest {
    val storedBytes = Base64.getEncoder().encode("cipher".toByteArray())
    every { crypto.decrypt(any()) } throws IllegalStateException("bad padding")

    val result = serializer.readFrom(ByteArrayInputStream(storedBytes))

    assertEquals(UserPreferences(), result)
  }

  @Test
  fun `readFrom falls back to the default value when the decrypted content is not valid JSON`() = runTest {
    val storedBytes = Base64.getEncoder().encode("cipher".toByteArray())
    every { crypto.decrypt(any()) } returns "not-json".toByteArray()

    val result = serializer.readFrom(ByteArrayInputStream(storedBytes))

    assertEquals(UserPreferences(), result)
  }

  @Test
  fun `readFrom falls back to the default value when the stored content is not valid Base64`() = runTest {
    val result = serializer.readFrom(ByteArrayInputStream("not-base64!!".toByteArray()))

    assertEquals(UserPreferences(), result)
    verify(exactly = 0) { crypto.decrypt(any()) }
  }

  @Test
  fun `writeTo encrypts the serialized preferences and writes them Base64-encoded`() = runTest {
    val preferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))
    val expectedPlainBytes = Json.encodeToString(preferences).toByteArray()
    val cipherBytes = "cipher".toByteArray()
    val plainBytesSlot = slot<ByteArray>()
    every { crypto.encrypt(capture(plainBytesSlot)) } returns cipherBytes
    val output = ByteArrayOutputStream()

    serializer.writeTo(preferences, output)

    assertEquals(String(expectedPlainBytes), String(plainBytesSlot.captured))
    assertEquals(
      String(Base64.getEncoder().encode(cipherBytes)),
      String(output.toByteArray())
    )
  }
}
