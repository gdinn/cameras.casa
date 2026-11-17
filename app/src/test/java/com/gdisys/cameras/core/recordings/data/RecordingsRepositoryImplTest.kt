package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.model.Recording
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

private const val CAM = "cam_10"

/** 2026-11-10 15:20: the 15:15 file is still being written, tomorrow's folder already exists. */
private val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 10, 15, 20)

/**
 * A share made of paths. Every folder on the way to a file exists; [emptyFolders] adds folders with
 * nothing in them. Each listing takes [listingMillis] of virtual time.
 */
private class FakeShare(
  files: List<String>,
  private val emptyFolders: Set<String> = emptySet(),
  private val listingMillis: Long = 0
) : RecordingsShare {
  private val paths = files + emptyFolders
  val listed = mutableListOf<String>()
  var failure: RecordingsException? = null
  var maxInFlight = 0
    private set
  private var inFlight = 0

  override suspend fun list(path: String): List<ShareEntry> {
    failure?.let { throw it }
    listed += path
    maxInFlight = maxOf(maxInFlight, ++inFlight)
    try {
      delay(listingMillis)
    } finally {
      inFlight--
    }
    return paths.filter { it.startsWith("$path/") }
      .map { full ->
        val rest = full.removePrefix("$path/")
        val name = rest.substringBefore('/')
        ShareEntry(name, isDirectory = rest.contains('/') || "$path/$name" in emptyFolders)
      }
      .distinct()
  }
}

private fun file(day: String, time: String, camera: String = CAM, separator: Char = '-'): String {
  val (y, m, d) = day.split('-')
  return "$camera/$y/$m/$d/${camera}_$y$separator$m$separator${d}_$time.mp4"
}

private fun recording(day: String, time: String): Recording {
  val (y, m, d) = day.split('-')
  val (h, min, _) = time.split('-')
  return Recording(
    CAM,
    LocalDateTime.of(y.toInt(), m.toInt(), d.toInt(), h.toInt(), min.toInt()),
    "${CAM}_$y-$m-${d}_$time.mp4"
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsRepositoryImplTest {

  private val clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

  private val share = FakeShare(
    files = listOf(
      file("2025-12-31", "23-45-00"),
      file("2026-01-01", "00-00-00"),
      file("2026-11-08", "23-45-00"),
      file("2026-11-10", "00-00-00"),
      file("2026-11-10", "15-00-01"),
      file("2026-11-10", "15-15-00") // being written
    ),
    emptyFolders = setOf("$CAM/2026/11/09", "$CAM/2026/11/11", "$CAM/2026/12")
  )

  private val repository = RecordingsRepositoryImpl(share, clock)

  // months

  @Test
  fun `months are listed in order, without the future`() = runTest {
    assertEquals(
      listOf(YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 11)),
      repository.months(CAM)
    )
  }

  @Test
  fun `folders that are not years or months are ignored`() = runTest {
    val share = FakeShare(
      files = listOf(file("2026-11-08", "10-00-00"), "$CAM/2026/notes.txt", "$CAM/readme.txt"),
      emptyFolders = setOf("$CAM/2026/13", "$CAM/2026/1", "$CAM/2026/ab", "$CAM/26", "$CAM/tmp", "$CAM/20261")
    )

    assertEquals(listOf(YearMonth.of(2026, 11)), RecordingsRepositoryImpl(share, clock).months(CAM))
  }

  @Test
  fun `a camera with no folder has no months`() = runTest {
    assertEquals(emptyList<YearMonth>(), repository.months("cam_99"))
  }

  @Test
  fun `an invalid camera name is refused before reaching the share`() = runTest {
    val thrown = runCatching { repository.months("../etc") }.exceptionOrNull()

    assertTrue(thrown is IllegalArgumentException)
    assertEquals(emptyList<String>(), share.listed)
  }

  // days

  @Test
  fun `only days with a finished file are listed`() = runTest {
    // 09 is empty, 11 is tomorrow's empty folder.
    assertEquals(
      listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10)),
      repository.days(CAM, YearMonth.of(2026, 11))
    )
  }

  @Test
  fun `a day whose only file is being written is not listed`() = runTest {
    val share = FakeShare(listOf(file("2026-11-10", "15-15-00")))

    assertEquals(emptyList<LocalDate>(), RecordingsRepositoryImpl(share, clock).days(CAM, YearMonth.of(2026, 11)))
  }

  @Test
  fun `the days of a month are listed in parallel, a bounded number at a time`() = runTest {
    val files = (1..30).map { day -> file("2026-09-${day.toString().padStart(2, '0')}", "10-00-00") }
    val share = FakeShare(files, listingMillis = 80)

    val days = RecordingsRepositoryImpl(share, clock).days(CAM, YearMonth.of(2026, 9))

    assertEquals(30, days.size)
    assertEquals(MAX_PARALLEL_LISTINGS, share.maxInFlight)
    // One listing for the month, then 30 days in ceil(30 / 8) = 4 rounds.
    assertEquals(80L * 5, currentTime)
  }

  // recordings

  @Test
  fun `the files of a day are in order, without seconds and without the one being written`() = runTest {
    assertEquals(
      listOf(recording("2026-11-10", "00-00-00"), recording("2026-11-10", "15-00-01")),
      repository.recordings(CAM, LocalDate.of(2026, 11, 10))
    )
    assertEquals(
      LocalDateTime.of(2026, 11, 10, 15, 0),
      repository.recordings(CAM, LocalDate.of(2026, 11, 10)).last().start
    )
  }

  @Test
  fun `files that are not recordings of this camera on this day are ignored`() = runTest {
    val share = FakeShare(
      files = listOf(
        file("2026-11-08", "10-00-00"),
        file("2026-11-08", "11-00-00", separator = '_'),
        file("2026-11-08", "12-00-00", camera = "cam_1").replace("cam_1/", "$CAM/"),
        "$CAM/2026/11/08/${CAM}_2026-11-07_23-45-00.mp4",
        "$CAM/2026/11/08/notes.txt",
        "$CAM/2026/11/08/sub/${CAM}_2026-11-08_13-00-00.mp4"
      )
    )

    assertEquals(
      listOf(LocalDateTime.of(2026, 11, 8, 10, 0), LocalDateTime.of(2026, 11, 8, 11, 0)),
      RecordingsRepositoryImpl(share, clock).recordings(CAM, LocalDate.of(2026, 11, 8)).map { it.start }
    )
  }

  @Test
  fun `two files of the same minute are ordered by name`() = runTest {
    val share = FakeShare(listOf(file("2026-11-08", "10-00-30"), file("2026-11-08", "10-00-00")))

    assertEquals(
      listOf("${CAM}_2026-11-08_10-00-00.mp4", "${CAM}_2026-11-08_10-00-30.mp4"),
      RecordingsRepositoryImpl(share, clock).recordings(CAM, LocalDate.of(2026, 11, 8)).map { it.fileName }
    )
  }

  // next / previous

  @Test
  fun `next stays on the same day when it can`() = runTest {
    assertEquals(recording("2026-11-10", "15-00-01"), repository.next(recording("2026-11-10", "00-00-00")))
  }

  @Test
  fun `next crosses midnight and skips empty days`() = runTest {
    // 11-09 has an empty folder.
    assertEquals(recording("2026-11-10", "00-00-00"), repository.next(recording("2026-11-08", "23-45-00")))
  }

  @Test
  fun `next crosses months and years`() = runTest {
    assertEquals(recording("2026-01-01", "00-00-00"), repository.next(recording("2025-12-31", "23-45-00")))
    assertEquals(recording("2026-11-08", "23-45-00"), repository.next(recording("2026-01-01", "00-00-00")))
  }

  @Test
  fun `next of the newest finished file is null`() = runTest {
    // The 15:15 file is still being written and tomorrow's folder is empty.
    assertNull(repository.next(recording("2026-11-10", "15-00-01")))
  }

  @Test
  fun `previous stays on the same day when it can`() = runTest {
    assertEquals(recording("2026-11-10", "00-00-00"), repository.previous(recording("2026-11-10", "15-00-01")))
  }

  @Test
  fun `previous crosses midnight, months and years`() = runTest {
    assertEquals(recording("2026-11-08", "23-45-00"), repository.previous(recording("2026-11-10", "00-00-00")))
    assertEquals(recording("2026-01-01", "00-00-00"), repository.previous(recording("2026-11-08", "23-45-00")))
    assertEquals(recording("2025-12-31", "23-45-00"), repository.previous(recording("2026-01-01", "00-00-00")))
  }

  @Test
  fun `previous of the oldest file is null`() = runTest {
    assertNull(repository.previous(recording("2025-12-31", "23-45-00")))
  }

  @Test
  fun `a neighbor is found even when the current file is gone`() = runTest {
    // 12:00 is not on the share (deleted by the retention, say).
    assertEquals(recording("2026-11-10", "15-00-01"), repository.next(recording("2026-11-10", "12-00-00")))
    assertEquals(recording("2026-11-10", "00-00-00"), repository.previous(recording("2026-11-10", "12-00-00")))
  }

  // errors

  @Test
  fun `a share failure reaches the caller as is`() = runTest {
    val cause = IOException("no route")
    share.failure = RecordingsException.Unreachable(cause)

    val thrown = runCatching { repository.days(CAM, YearMonth.of(2026, 11)) }.exceptionOrNull()

    // Same type and cause, not always the same instance: in debug mode, coroutines copy an exception
    // that leaves a coroutineScope, with the original as the copy's cause.
    assertTrue(thrown is RecordingsException.Unreachable)
    assertTrue(generateSequence(thrown) { it.cause }.any { it === cause })
  }
}
