package com.gdisys.cameras.feature.history.logic

import com.gdisys.cameras.core.recordings.domain.model.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

private fun at(time: String, seconds: String = "00"): Recording {
  val (h, m) = time.split(':').map { it.toInt() }
  return Recording(
    "cam_10",
    LocalDateTime.of(2026, 11, 10, h, m),
    "cam_10_2026-11-10_${"%02d".format(h)}-${"%02d".format(m)}-$seconds.mp4"
  )
}

private fun slot(time: String) = LocalTime.parse(time)

class TimeSlotsTest {

  @Test
  fun `a day has 96 slots of 15 minutes`() {
    assertEquals(96, SLOTS_PER_DAY)
    assertEquals(96, DAY_SLOTS.size)
    assertEquals(slot("00:00"), DAY_SLOTS.first())
    assertEquals(slot("00:15"), DAY_SLOTS[1])
    assertEquals(slot("23:45"), DAY_SLOTS.last())
  }

  @Test
  fun `a recording highlights the slot it starts in`() {
    assertEquals(
      setOf(slot("15:00"), slot("15:15"), slot("23:45")),
      recordedSlots(listOf(at("15:00"), at("15:10"), at("15:15"), at("23:59")))
    )
  }

  @Test
  fun `an exact match is chosen`() {
    val choice = nearestRecording(listOf(at("14:45"), at("15:00"), at("15:15")), slot("15:00"))

    assertEquals(at("15:00"), choice?.recording)
    assertTrue(choice!!.startsAtRequestedSlot)
  }

  @Test
  fun `a later recording is found when nothing comes before`() {
    val choice = nearestRecording(listOf(at("15:10"), at("16:00")), slot("15:00"))

    assertEquals(at("15:10"), choice?.recording)
    assertFalse(choice!!.startsAtRequestedSlot)
  }

  @Test
  fun `an earlier recording is found when nothing comes after`() {
    val choice = nearestRecording(listOf(at("09:00"), at("14:30")), slot("15:00"))

    assertEquals(at("14:30"), choice?.recording)
    assertFalse(choice!!.startsAtRequestedSlot)
  }

  @Test
  fun `the closer side wins`() {
    assertEquals(at("14:55"), nearestRecording(listOf(at("14:55"), at("15:10")), slot("15:00"))?.recording)
    assertEquals(at("15:04"), nearestRecording(listOf(at("14:55"), at("15:04")), slot("15:00"))?.recording)
  }

  @Test
  fun `a tie goes to the later recording`() {
    val choice = nearestRecording(listOf(at("14:50"), at("15:10")), slot("15:00"))

    assertEquals(at("15:10"), choice?.recording)
  }

  @Test
  fun `two recordings of the same minute go to the later file`() {
    val choice = nearestRecording(listOf(at("15:00", "00"), at("15:00", "30")), slot("15:00"))

    assertEquals(at("15:00", "30"), choice?.recording)
    assertTrue(choice!!.startsAtRequestedSlot)
  }

  @Test
  fun `seconds do not count`() {
    // 15:00:59 is the 15:00 recording, so it starts at the slot.
    val choice = nearestRecording(listOf(at("15:00", "59")), slot("15:00"))

    assertTrue(choice!!.startsAtRequestedSlot)
  }

  @Test
  fun `the search stays at both ends of the day`() {
    assertEquals(at("00:20"), nearestRecording(listOf(at("00:20"), at("12:00")), slot("00:00"))?.recording)
    assertEquals(at("23:10"), nearestRecording(listOf(at("12:00"), at("23:10")), slot("23:45"))?.recording)
  }

  @Test
  fun `an empty day has no choice`() {
    assertNull(nearestRecording(emptyList(), slot("15:00")))
  }

  @Test
  fun `the choice remembers the requested slot`() {
    assertEquals(slot("15:00"), nearestRecording(listOf(at("15:10")), slot("15:00"))?.requestedSlot)
  }
}
