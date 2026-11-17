package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.model.Recording
import java.time.LocalDate
import java.time.YearMonth

/** The share's layout: `cam_xx/YYYY/MM/DD/<file>.mp4`. */
internal object RecordingPaths {

  fun year(camera: String, year: Int): String = "$camera/${year.pad(4)}"

  fun month(camera: String, month: YearMonth): String = "${year(camera, month.year)}/${month.monthValue.pad(2)}"

  fun day(camera: String, day: LocalDate): String =
    "${month(camera, YearMonth.from(day))}/${day.dayOfMonth.pad(2)}"

  fun file(recording: Recording): String =
    "${day(recording.camera, recording.start.toLocalDate())}/${recording.fileName}"

  // padStart, not String.format: the digits must not follow the device's locale.
  private fun Int.pad(width: Int) = toString().padStart(width, '0')
}
