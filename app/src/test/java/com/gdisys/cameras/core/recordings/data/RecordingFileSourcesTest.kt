package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.time.LocalDateTime

class RecordingFileSourcesTest {

  @Test
  fun `each source is a new session, which connects only when a file is opened`() {
    val sources = RecordingFileSources(mockk<UserPreferencesRepository>())

    val first = sources.create()
    val second = sources.create()

    assertNotSame(first, second)
    first.close()
    second.close()
  }

  @Test
  fun `a recording is opened at its path on the share`() {
    val opened = mutableListOf<String>()
    val source = object : RecordingFileSource {
      override fun open(path: String): RemoteFile {
        opened += path
        return object : RemoteFile {
          override val length = 0L
          override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) = -1
          override fun close() = Unit
        }
      }
      override fun close() = Unit
    }

    source.open(Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4"))

    assertEquals(listOf("cam_10/2026/11/10/cam_10_2026-11-10_15-00-00.mp4"), opened)
  }
}
