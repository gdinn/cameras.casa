package com.gdisys.cameras.core.recordings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RecordingSlotsTest {

  private fun at(hour: Int, minute: Int, second: Int = 0) = LocalDateTime.of(2026, 11, 10, hour, minute, second)

  @Test
  fun `a time belongs to the 15-minute slot it falls in`() {
    assertEquals(at(15, 0), at(15, 0).slotStart())
    assertEquals(at(15, 0), at(15, 14, 59).slotStart())
    assertEquals(at(15, 15), at(15, 15).slotStart())
    assertEquals(at(15, 45), at(15, 59).slotStart())
  }

  @Test
  fun `a file that starts late still ends on the boundary`() {
    assertEquals(at(15, 15), at(15, 10).slotEnd())
    assertEquals(at(15, 15), at(15, 0).slotEnd())
  }

  @Test
  fun `the last slot of the day ends at midnight`() {
    assertEquals(LocalDateTime.of(2026, 11, 11, 0, 0), at(23, 50).slotEnd())
  }

  @Test
  fun `a file is finished only once its slot and the settle time are over`() {
    val start = at(15, 0)
    assertFalse(isRecordingFinished(start, at(15, 10)))
    assertFalse(isRecordingFinished(start, at(15, 15, 30)))
    assertTrue(isRecordingFinished(start, at(15, 16)))
    assertTrue(isRecordingFinished(start, at(18, 0)))
  }
}
