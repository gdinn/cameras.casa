package com.gdisys.cameras.core.recordings.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The recorder cuts a file every 15 minutes on the clock (`-segment_atclocktime 1`), so a file never
 * crosses `:00`, `:15`, `:30` or `:45`. A file that starts at 15:10, after a reconnect, ends at 15:15.
 */
const val RECORDING_SLOT_MINUTES = 15

/**
 * How long after its slot ends a file still counts as being written. The recorder closes the file
 * on the boundary and then moves its index to the front (`+faststart`), which rewrites the whole
 * file; until then it cannot be played. The margin also absorbs a small clock difference between
 * the phone and the Pi.
 */
val RECORDING_SETTLE_TIME: Duration = Duration.ofMinutes(1)

/** The start of the 15-minute slot [this] falls in. */
fun LocalDateTime.slotStart(): LocalDateTime {
  val hour = truncatedTo(ChronoUnit.HOURS)
  return hour.plusMinutes((minute / RECORDING_SLOT_MINUTES * RECORDING_SLOT_MINUTES).toLong())
}

/** The end of the 15-minute slot [this] falls in, which is where a file starting at [this] ends. */
fun LocalDateTime.slotEnd(): LocalDateTime = slotStart().plusMinutes(RECORDING_SLOT_MINUTES.toLong())

/**
 * Whether a file that starts at [start] is finished at [now]. Only the newest file can fail this:
 * any other file's slot is over by definition.
 */
fun isRecordingFinished(start: LocalDateTime, now: LocalDateTime): Boolean =
  !now.isBefore(start.slotEnd().plus(RECORDING_SETTLE_TIME))
