package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.model.Recording
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class RecordingPathsTest {

  @Test
  fun `folders are zero-padded`() {
    assertEquals("cam_10/2026", RecordingPaths.year("cam_10", 2026))
    assertEquals("cam_10/2026/01", RecordingPaths.month("cam_10", YearMonth.of(2026, 1)))
    assertEquals("cam_10/2026/01/05", RecordingPaths.day("cam_10", LocalDate.of(2026, 1, 5)))
  }

  @Test
  fun `a file lives in the folder of its day`() {
    val recording = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-01.mp4")

    assertEquals("cam_10/2026/11/10/cam_10_2026-11-10_15-00-01.mp4", RecordingPaths.file(recording))
  }
}
