package com.gdisys.cameras.testing

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

/**
 * Proves `TestRecordingsModule` puts the production repository over [FakeRecordingsShare], with
 * the clock fixed at [RECORDINGS_NOW].
 */
@HiltAndroidTest
class FakeRecordingsShareTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var share: FakeRecordingsShare

  @Inject
  lateinit var repository: RecordingsRepository

  @Before
  fun injectDependencies() {
    hiltRule.inject()
  }

  @Test
  fun theRepositoryReadsTheFakeShareAtTheFixedTime() = runBlocking {
    share.files = listOf(
      "cam_10/2026/11/09/cam_10_2026-11-09_23-45-00.mp4",
      "cam_10/2026/11/10/cam_10_2026-11-10_15-00-00.mp4",
      "cam_10/2026/11/10/cam_10_2026-11-10_15-15-00.mp4" // being written at 15:20
    )
    share.emptyFolders = setOf("cam_10/2026/11/11")

    assertEquals(listOf(YearMonth.of(2026, 11)), repository.months("cam_10"))
    assertEquals(
      listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)),
      repository.days("cam_10", YearMonth.of(2026, 11))
    )
    assertEquals(
      listOf(LocalTime.of(15, 0)),
      repository.recordings("cam_10", LocalDate.of(2026, 11, 10)).map { it.start.toLocalTime() }
    )
  }

  @Test
  fun aFailureIsThrownByEveryListing() = runBlocking {
    val cause = IOException("no route")
    share.failure = RecordingsException.Unreachable(cause)

    val thrown = runCatching { repository.months("cam_10") }.exceptionOrNull()

    // Type and cause rather than identity: with coroutine debug mode on, an exception leaving a
    // coroutineScope is copied, with the original as the copy's cause.
    assertTrue(thrown is RecordingsException.Unreachable)
    assertTrue(generateSequence(thrown) { it.cause }.any { it === cause })
  }
}
