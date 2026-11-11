package com.gdisys.cameras.core.recordings.domain.usecase

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
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
import java.time.YearMonth

class RecordingsUseCasesTest {

  private val repository = mockk<RecordingsRepository>()
  private val months = GetRecordingMonthsUseCase(repository)
  private val days = GetRecordingDaysUseCase(repository)

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
  fun `a failure becomes a failed result`() = runTest {
    val failure = RecordingsException.Unreachable(IOException("no route"))
    coEvery { repository.months(any()) } throws failure
    coEvery { repository.days(any(), any()) } throws failure

    assertSame(failure, months("cam_10").exceptionOrNull())
    assertSame(failure, days("cam_10", YearMonth.of(2026, 11)).exceptionOrNull())
  }

  @Test
  fun `a cancellation is not turned into a result`() = runTest {
    coEvery { repository.months(any()) } throws CancellationException("left the screen")

    val thrown = runCatching { months("cam_10") }.exceptionOrNull()

    assertTrue(thrown is CancellationException)
  }
}
