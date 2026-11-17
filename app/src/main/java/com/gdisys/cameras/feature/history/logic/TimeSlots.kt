package com.gdisys.cameras.feature.history.logic

import com.gdisys.cameras.core.recordings.domain.RECORDING_SLOT_MINUTES
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.recordings.domain.slotStart
import java.time.LocalTime
import kotlin.math.abs

/** 96: the 15-minute slots of a day, which is what the time picker offers. */
const val SLOTS_PER_DAY = 24 * 60 / RECORDING_SLOT_MINUTES

/** The start of every slot of a day, from 00:00 to 23:45. */
val DAY_SLOTS: List<LocalTime> = List(SLOTS_PER_DAY) { LocalTime.MIDNIGHT.plusMinutes(it * RECORDING_SLOT_MINUTES.toLong()) }

/** The slots a recording starts in, which the picker highlights. */
fun recordedSlots(recordings: List<Recording>): Set<LocalTime> =
  recordings.mapTo(mutableSetOf()) { it.start.slotStart().toLocalTime() }

/**
 * The recording picked for [requestedSlot]. [startsAtRequestedSlot] is false when the nearest
 * recording starts elsewhere, which the user is told.
 */
data class RecordingChoice(val recording: Recording, val requestedSlot: LocalTime) {
  val startsAtRequestedSlot: Boolean get() = recording.start.toLocalTime() == requestedSlot
}

/**
 * The recording of the day whose start is closest to [slot], looking forwards and backwards. On a
 * tie the later one wins: the user asked for a time and gets what came after it. `null` for a day
 * without recordings. The [recordings] are those of a single day, so the search never leaves it.
 */
fun nearestRecording(recordings: List<Recording>, slot: LocalTime): RecordingChoice? {
  val slotMinute = slot.toSecondOfDay() / 60
  val distance = { recording: Recording -> abs(recording.start.toLocalTime().toSecondOfDay() / 60 - slotMinute) }
  return recordings
    .minWithOrNull(compareBy(distance).then(Recording.ORDER.reversed()))
    ?.let { RecordingChoice(it, slot) }
}
