package com.gdisys.cameras.core.recordings.domain.usecase

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import com.gdisys.cameras.core.recordings.domain.model.Recording
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class RecordingsUseCasesTest {

  private val repository = mockk<RecordingsRepository>()
  private val months = GetRecordingMonthsUseCase(repository)
  private val days = GetRecordingDaysUseCase(repository)
  private val recordings = GetRecordingsOfDayUseCase(repository)
  private val neighbor = GetNeighborRecordingUseCase(repository)

  @Test
  fun `months come newest first`() = runTest {
    coEvery { repository.months("cam_10") } returns listOf(YearMonth.of(2025, 12), YearMonth.of(2026, 11))

    assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2025, 12)), months("cam_10").getOrThrow())
  }

  @Test
  fun `days keep the calendar order`() = runTest {
    val list = listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10))
    coEvery { repository.days("cam_10", YearMonth.of(2026, 11)) } returns list

    assertEquals(list, days("cam_10", YearMonth.of(2026, 11)).getOrThrow())
  }

  @Test
  fun `the recordings of a day keep their order`() = runTest {
    val day = LocalDate.of(2026, 11, 10)
    val list = listOf(
      Recording("cam_10", LocalDateTime.of(2026, 11, 10, 0, 0), "cam_10_2026-11-10_00-00-00.mp4"),
      Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4")
    )
    coEvery { repository.recordings("cam_10", day) } returns list

    assertEquals(list, recordings("cam_10", day).getOrThrow())
  }

  @Test
  fun `the neighbor follows the direction`() = runTest {
    val current = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4")
    val before = current.copy(start = LocalDateTime.of(2026, 11, 10, 14, 45), fileName = "cam_10_2026-11-10_14-45-00.mp4")
    coEvery { repository.previous(current) } returns before
    coEvery { repository.next(current) } returns null

    assertEquals(before, neighbor(current, RecordingDirection.PREVIOUS).getOrThrow())
    assertEquals(null, neighbor(current, RecordingDirection.NEXT).getOrThrow())
  }

  @Test
  fun `a failure becomes a failed result`() = runTest {
    val failure = RecordingsException.Unreachable(IOException("no route"))
    coEvery { repository.months(any()) } throws failure
    coEvery { repository.days(any(), any()) } throws failure
    coEvery { repository.recordings(any(), any()) } throws failure
    coEvery { repository.next(any()) } throws failure

    assertSame(failure, months("cam_10").exceptionOrNull())
    assertSame(failure, days("cam_10", YearMonth.of(2026, 11)).exceptionOrNull())
    assertSame(failure, recordings("cam_10", LocalDate.of(2026, 11, 10)).exceptionOrNull())
    val anyRecording = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4")
    assertSame(failure, neighbor(anyRecording, RecordingDirection.NEXT).exceptionOrNull())
  }

  @Test
  fun `a cancellation is not turned into a result`() = runTest {
    coEvery { repository.months(any()) } throws CancellationException("left the screen")

    val thrown = runCatching { months("cam_10") }.exceptionOrNull()

    assertTrue(thrown is CancellationException)
  }
}
