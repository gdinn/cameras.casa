package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.RecordingFileName
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import com.gdisys.cameras.core.recordings.domain.isRecordingFinished
import com.gdisys.cameras.core.recordings.domain.model.Recording
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

/**
 * Listings at once when a month is expanded. One day folder takes ~80 ms through the tunnel, so a
 * month listed in sequence would take ~2.5 s; all requests share one SMB connection.
 */
internal const val MAX_PARALLEL_LISTINGS = 8

private val YEAR = Regex("""\d{4}""")
private val TWO_DIGITS = Regex("""\d{2}""")

/** [RecordingsRepository] over the share's folder layout (see [RecordingPaths]). */
class RecordingsRepositoryImpl @Inject constructor(
  private val share: RecordingsShare,
  private val clock: Clock
) : RecordingsRepository {

  override suspend fun months(camera: String): List<YearMonth> {
    requireCamera(camera)
    val now = YearMonth.now(clock)
    val years = share.list(camera).directories().filter { YEAR.matches(it) }.map { it.toInt() }
    return coroutineScope {
      years.map { year ->
        async {
          share.list(RecordingPaths.year(camera, year)).directories()
            .mapNotNull { name -> parseMonth(year, name) }
        }
      }.awaitAll()
    }.flatten().filter { it <= now }.sorted()
  }

  override suspend fun days(camera: String, month: YearMonth): List<LocalDate> {
    requireCamera(camera)
    val permits = Semaphore(MAX_PARALLEL_LISTINGS)
    return coroutineScope {
      dayFolders(camera, month).map { day ->
        async { permits.withPermit { day.takeIf { recordingsOf(camera, day).isNotEmpty() } } }
      }.awaitAll()
    }.filterNotNull()
  }

  override suspend fun recordings(camera: String, day: LocalDate): List<Recording> {
    requireCamera(camera)
    return recordingsOf(camera, day)
  }

  override suspend fun next(recording: Recording): Recording? {
    requireCamera(recording.camera)
    val camera = recording.camera
    val day = recording.start.toLocalDate()
    recordingsOf(camera, day).firstOrNull { Recording.ORDER.compare(it, recording) > 0 }?.let { return it }
    for (month in months(camera).filter { it >= YearMonth.from(day) }) {
      for (laterDay in dayFolders(camera, month).filter { it > day }) {
        recordingsOf(camera, laterDay).firstOrNull()?.let { return it }
      }
    }
    return null
  }

  override suspend fun previous(recording: Recording): Recording? {
    requireCamera(recording.camera)
    val camera = recording.camera
    val day = recording.start.toLocalDate()
    recordingsOf(camera, day).lastOrNull { Recording.ORDER.compare(it, recording) < 0 }?.let { return it }
    for (month in months(camera).filter { it <= YearMonth.from(day) }.asReversed()) {
      for (earlierDay in dayFolders(camera, month).filter { it < day }.asReversed()) {
        recordingsOf(camera, earlierDay).lastOrNull()?.let { return it }
      }
    }
    return null
  }

  /** The day folders of [month] up to today, whether or not they hold a finished file. */
  private suspend fun dayFolders(camera: String, month: YearMonth): List<LocalDate> {
    val today = LocalDate.now(clock)
    return share.list(RecordingPaths.month(camera, month)).directories()
      .mapNotNull { name -> parseDay(month, name) }
      .filter { it <= today }
      .sorted()
  }

  private suspend fun recordingsOf(camera: String, day: LocalDate): List<Recording> {
    val now = LocalDateTime.now(clock)
    return share.list(RecordingPaths.day(camera, day))
      .filterNot { it.isDirectory }
      .mapNotNull { entry ->
        RecordingFileName.parse(camera, entry.name)
          // A file filed under the wrong day would get a path that does not exist.
          ?.takeIf { it.toLocalDate() == day && isRecordingFinished(it, now) }
          ?.let { start -> Recording(camera, start, entry.name) }
      }
      .sortedWith(Recording.ORDER)
  }

  private fun requireCamera(camera: String) =
    require(RecordingFileName.isValidCamera(camera)) { "Not a camera name: $camera" }

  private fun List<ShareEntry>.directories() = filter { it.isDirectory }.map { it.name }

  private fun parseMonth(year: Int, name: String): YearMonth? =
    if (TWO_DIGITS.matches(name)) catchingDate { YearMonth.of(year, name.toInt()) } else null

  private fun parseDay(month: YearMonth, name: String): LocalDate? =
    if (TWO_DIGITS.matches(name)) catchingDate { month.atDay(name.toInt()) } else null

  private inline fun <T> catchingDate(block: () -> T): T? = try {
    block()
  } catch (_: DateTimeException) {
    null
  }
}
