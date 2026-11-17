package com.gdisys.cameras.core.recordings.domain.model

import java.time.LocalDateTime

/**
 * One file the recorder left on the share.
 *
 * [start] is the time in the file name without its seconds, as the user sees it: the recorder's
 * clock and the phone share a time zone, so no conversion happens. Two files can start on the same
 * minute (a reconnect right after a restart), so [fileName] is what tells them apart.
 */
data class Recording(
  val camera: String,
  val start: LocalDateTime,
  val fileName: String
) {
  companion object {
    /** Chronological order, with the file name breaking a tie between files of the same minute. */
    val ORDER: Comparator<Recording> = compareBy<Recording>({ it.start }, { it.fileName })
  }
}
