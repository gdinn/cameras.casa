package com.gdisys.cameras.core.recordings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RecordingFileNameTest {

  @Test
  fun `the recorder's format is parsed`() {
    assertEquals(
      LocalDateTime.of(2026, 11, 10, 15, 0),
      RecordingFileName.parse("cam_10", "cam_10_2026-11-10_15-00-00.mp4")
    )
  }

  @Test
  fun `the underscore format is parsed`() {
    assertEquals(
      LocalDateTime.of(2026, 11, 10, 15, 0),
      RecordingFileName.parse("cam_10", "cam_10_2026_11_10_15-00-00.mp4")
    )
  }

  @Test
  fun `seconds are dropped`() {
    assertEquals(
      LocalDateTime.of(2026, 11, 11, 11, 0),
      RecordingFileName.parse("cam_10", "cam_10_2026-11-11_11-00-01.mp4")
    )
    assertEquals(
      LocalDateTime.of(2026, 11, 11, 11, 10),
      RecordingFileName.parse("cam_10", "cam_10_2026-11-11_11-10-59.mp4")
    )
  }

  @Test
  fun `a file of another camera is not a recording of this one`() {
    assertNull(RecordingFileName.parse("cam_1", "cam_10_2026-11-10_15-00-00.mp4"))
    assertNull(RecordingFileName.parse("cam_10", "cam_1_2026-11-10_15-00-00.mp4"))
  }

  @Test
  fun `names that are not recordings are ignored`() {
    listOf(
      "cam_10_2026-11-10_15-00-00.mkv",
      "cam_10_2026-11-10_15-00-00.mp4.part",
      ".cam_10_2026-11-10_15-00-00.mp4",
      "cam_10_2026-11_10_15-00-00.mp4", // mixed date separators
      "cam_10_2026-11-10_15-00.mp4",
      "cam_10_26-11-10_15-00-00.mp4",
      "cam_10_2026-11-10 15-00-00.mp4",
      "notes.txt",
      ""
    ).forEach { name -> assertNull(name, RecordingFileName.parse("cam_10", name)) }
  }

  @Test
  fun `impossible dates and times are ignored`() {
    listOf(
      "cam_10_2026-02-30_15-00-00.mp4",
      "cam_10_2026-13-01_15-00-00.mp4",
      "cam_10_2026-11-10_24-00-00.mp4",
      "cam_10_2026-11-10_15-60-00.mp4",
      "cam_10_2026-11-10_15-00-60.mp4"
    ).forEach { name -> assertNull(name, RecordingFileName.parse("cam_10", name)) }
  }

  @Test
  fun `camera names are cam_ plus digits`() {
    assertTrue(RecordingFileName.isValidCamera("cam_10"))
    assertTrue(RecordingFileName.isValidCamera("cam_160"))
    listOf("cam_", "cam_a", "cam_10/..", "../cam_10", "cam_10/2026", "CAM_10", "cam10", "").forEach {
      assertFalse(it, RecordingFileName.isValidCamera(it))
    }
  }
}
