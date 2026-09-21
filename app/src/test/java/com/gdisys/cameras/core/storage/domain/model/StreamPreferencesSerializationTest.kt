package com.gdisys.cameras.core.storage.domain.model

import com.gdisys.cameras.core.storage.data.EncryptedPreferencesSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the on-disk schema.
 *
 * `EncryptedPreferencesSerializer` falls back to the default value on any decoding failure, so a
 * schema change does not crash — it silently erases the user's cameras and grid. These tests turn
 * that silent loss into a red build.
 */
class StreamPreferencesSerializationTest {

  private val json = EncryptedPreferencesSerializer.json

  @Test
  fun `serial names are stable`() {
    val encoded = json.encodeToString(
      StreamPreferences.serializer(),
      StreamPreferences(
        streamUrls = listOf("a"),
        portraitOrder = listOf("a"),
        landscapeOrder = listOf("a"),
        portraitGrid = GridPreferences(columns = 1, rows = 2, dynamicRows = true),
        landscapeGrid = GridPreferences(columns = 3, rows = 4, dynamicRows = false)
      )
    )

    assertEquals(
      """{"streamUrls":["a"],"portraitOrder":["a"],"landscapeOrder":["a"],""" +
        """"portraitGrid":{"columns":1,"rows":2,"dynamicRows":true},""" +
        """"landscapeGrid":{"columns":3,"rows":4,"dynamicRows":false},"schemaVersion":1}""",
      encoded
    )
  }

  @Test
  fun `decodes a payload written before the schema version field existed`() {
    val legacy = """{"streamUrls":["a"],"portraitOrder":["a"],"landscapeOrder":["a"],""" +
      """"portraitGrid":{"columns":1,"rows":1,"dynamicRows":true},""" +
      """"landscapeGrid":{"columns":2,"rows":1,"dynamicRows":true}}"""

    val decoded = json.decodeFromString(StreamPreferences.serializer(), legacy)

    assertEquals(listOf("a"), decoded.streamUrls)
    assertEquals(STREAM_PREFERENCES_SCHEMA_VERSION, decoded.schemaVersion)
  }

  @Test
  fun `decodes an empty object into the defaults`() {
    val decoded = json.decodeFromString(StreamPreferences.serializer(), "{}")

    assertEquals(StreamPreferences(), decoded)
  }

  @Test
  fun `round trips without losing anything`() {
    val preferences = StreamPreferences(
      streamUrls = listOf("a", "b"),
      portraitOrder = listOf("b", "a"),
      landscapeOrder = listOf("a", "b"),
      portraitGrid = GridPreferences(columns = 2, rows = 3, dynamicRows = false),
      landscapeGrid = GridPreferences(columns = 4, rows = 1, dynamicRows = true)
    )

    val encoded = json.encodeToString(StreamPreferences.serializer(), preferences)

    assertEquals(preferences, json.decodeFromString(StreamPreferences.serializer(), encoded))
  }
}
