package com.gdisys.cameras.core.recordings.domain

import java.time.DateTimeException
import java.time.LocalDateTime

/**
 * The names the recorder writes on the share.
 *
 * - Camera: `cam_` plus digits, the same name as in the stream URL path (`.../cam_10/whep`).
 * - File: `cam_10_2026-11-10_15-00-00.mp4`, which is what the recorder writes, or
 *   `cam_10_2026_11_10_15-00-00.mp4`. The date uses one separator throughout.
 *
 * Seconds are checked but dropped: a file that starts at `15-00-01` is the 15:00 recording.
 */
object RecordingFileName {

  private val CAMERA = Regex("cam_[0-9]+")

  private val FILE = Regex("""(cam_[0-9]+)_(\d{4})([-_])(\d{2})\3(\d{2})_(\d{2})-(\d{2})-(\d{2})\.mp4""")

  /** Also what keeps a camera name from walking out of its folder on the share. */
  fun isValidCamera(camera: String): Boolean = CAMERA.matches(camera)

  /** The start of [fileName], or `null` when it is not a recording of [camera]. */
  fun parse(camera: String, fileName: String): LocalDateTime? {
    val match = FILE.matchEntire(fileName) ?: return null
    val (name, year, _, month, day, hour, minute, second) = match.destructured
    if (name != camera) return null
    return try {
      LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
        .withSecond(0)
    } catch (_: DateTimeException) {
      null
    }
  }
}
