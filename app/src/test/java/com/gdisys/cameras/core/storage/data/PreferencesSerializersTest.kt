package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PreferencesSerializersTest {

  // Reversible and Keystore-free, so the round trip runs on the JVM.
  private val crypto = object : CryptoEngine {
    override fun encrypt(bytes: ByteArray) = bytes.reversedArray()
    override fun decrypt(bytes: ByteArray) = bytes.reversedArray()
  }

  @Test
  fun `released file names and key aliases are unchanged`() {
    // Renaming either one orphans every installed user's settings: the new file is empty, or the
    // new key cannot decrypt the old file and the serializer falls back to the default value.
    assertEquals("user-preferences", USER_PREFERENCES_FILE_NAME)
    assertEquals("gdi-sys-storage", USER_PREFERENCES_KEY_ALIAS)
    assertEquals("stream-preferences", STREAM_PREFERENCES_FILE_NAME)
    assertEquals("gdi-sys-stream-preferences", STREAM_PREFERENCES_KEY_ALIAS)
  }

  @Test
  fun `userPreferencesSerializer defaults to empty preferences and round-trips through crypto`() = runTest {
    val serializer = userPreferencesSerializer(crypto)
    val preferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))

    val output = ByteArrayOutputStream()
    serializer.writeTo(preferences, output)

    assertEquals(UserPreferences(), serializer.defaultValue)
    assertEquals(preferences, serializer.readFrom(ByteArrayInputStream(output.toByteArray())))
  }

  @Test
  fun `streamPreferencesSerializer defaults to the stream defaults and round-trips through crypto`() = runTest {
    val serializer = streamPreferencesSerializer(crypto)
    val preferences = StreamPreferences(streamUrls = listOf("http://[fd00:20::cafe]:8889/cam_1"))

    val output = ByteArrayOutputStream()
    serializer.writeTo(preferences, output)

    assertEquals(StreamPreferences(), serializer.defaultValue)
    assertEquals(preferences, serializer.readFrom(ByteArrayInputStream(output.toByteArray())))
  }
}
