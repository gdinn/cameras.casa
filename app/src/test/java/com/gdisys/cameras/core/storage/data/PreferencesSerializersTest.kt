package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.DownloadRecord
import com.gdisys.cameras.core.storage.domain.model.DownloadRecordState
import com.gdisys.cameras.core.storage.domain.model.DownloadRecords
import com.gdisys.cameras.core.storage.domain.model.SmbCredentials
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

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
    assertEquals("download-records", DOWNLOAD_RECORDS_FILE_NAME)
    assertEquals("gdi-sys-download-records", DOWNLOAD_RECORDS_KEY_ALIAS)
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
  fun `userPreferencesSerializer keeps the Samba login`() = runTest {
    val serializer = userPreferencesSerializer(crypto)
    val preferences = UserPreferences(
      vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"),
      smbCredentials = SmbCredentials(smbUser = "admin-a-7", smbPassword = "secret")
    )

    val output = ByteArrayOutputStream()
    serializer.writeTo(preferences, output)

    assertEquals(preferences, serializer.readFrom(ByteArrayInputStream(output.toByteArray())))
  }

  @Test
  fun `userPreferencesSerializer reads a file written before the Samba login existed`() = runTest {
    // What every installed app has on disk today: the VPN fields only.
    val stored = """{"vpnConfigDefaults":null,"vpnConfigTokens":{"iPrk":"private-key","iAddr":null,"pPsk":null}}"""
    val file = Base64.getEncoder().encode(crypto.encrypt(stored.toByteArray()))

    val read = userPreferencesSerializer(crypto).readFrom(ByteArrayInputStream(file))

    assertEquals(UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key")), read)
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

  @Test
  fun `downloadRecordsSerializer defaults to no records and round-trips through crypto`() = runTest {
    val serializer = downloadRecordsSerializer(crypto)
    val records = DownloadRecords(
      listOf(
        DownloadRecord("a", "cam_10", "cam_10_2026-11-10_15-00-00.mp4", DownloadRecordState.INTERRUPTED, "content://media/1")
      )
    )

    val output = ByteArrayOutputStream()
    serializer.writeTo(records, output)

    assertEquals(DownloadRecords(), serializer.defaultValue)
    assertEquals(records, serializer.readFrom(ByteArrayInputStream(output.toByteArray())))
  }

  @Test
  fun `download records keep their serial names`() {
    val encoded = EncryptedPreferencesSerializer.json.encodeToString(
      DownloadRecords.serializer(),
      DownloadRecords(listOf(DownloadRecord("a", "cam_10", "f.mp4", DownloadRecordState.FAILED)))
    )

    assertEquals(
      """{"records":[{"workId":"a","camera":"cam_10","fileName":"f.mp4","state":"failed","partialFile":null}],""" +
        """"schemaVersion":1}""",
      encoded
    )
  }
}
