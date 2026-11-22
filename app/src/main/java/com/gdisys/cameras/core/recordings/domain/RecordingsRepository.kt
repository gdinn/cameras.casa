package com.gdisys.cameras.core.recordings.domain

import com.gdisys.cameras.core.recordings.domain.model.Recording
import java.time.LocalDate
import java.time.YearMonth

/**
 * The recordings of one camera on the Samba share, as the stored Samba login sees them.
 *
 * - Every call reads the share: nothing is cached, because the recorder adds a file every 15
 *   minutes.
 * - Lists are in chronological order; a screen that wants the newest first reverses them.
 * - Only finished files count (see [isRecordingFinished]): the file being written cannot be played.
 * - Nothing in the future is listed. The recorder creates tomorrow's folder ahead of time, empty.
 * - A camera with no folder on the share simply has no recordings.
 *
 * Every function requires a valid camera name ([RecordingFileName.isValidCamera]) and throws only
 * [RecordingsException] otherwise.
 */
interface RecordingsRepository {

  /** The months that have a folder for [camera]. A month's days may still all be empty. */
  suspend fun months(camera: String): List<YearMonth>

  /** The days of [month] with at least one finished file. */
  suspend fun days(camera: String, month: YearMonth): List<LocalDate>

  /** The finished files of [day]. */
  suspend fun recordings(camera: String, day: LocalDate): List<Recording>

  /** The file right after [recording], on the same day or a later one, or `null` at the end. */
  suspend fun next(recording: Recording): Recording?

  /** The file right before [recording], on the same day or an earlier one, or `null` at the start. */
  suspend fun previous(recording: Recording): Recording?
}
