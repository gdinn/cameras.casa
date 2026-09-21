package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveGridPreferencesUseCaseTest {

  private val streamPreferencesRepository = mockk<StreamPreferencesRepository>()
  private lateinit var useCase: SaveGridPreferencesUseCase

  private val portraitGrid = GridPreferences(columns = 2, rows = 3, dynamicRows = false)
  private val landscapeGrid = GridPreferences(columns = 4, rows = 2, dynamicRows = false)

  @Before
  fun setUp() {
    useCase = SaveGridPreferencesUseCase(streamPreferencesRepository)
  }

  @Test
  fun `overwrites only the grids, leaving urls and orders untouched`() = runTest {
    val transform = slot<(StreamPreferences) -> StreamPreferences>()
    coEvery { streamPreferencesRepository.updateStreamPreferences(capture(transform)) } returns Unit
    val current = StreamPreferences(
      streamUrls = listOf("a", "b"),
      portraitOrder = listOf("b", "a"),
      landscapeOrder = listOf("a", "b")
    )

    val result = useCase(portraitGrid, landscapeGrid)

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { streamPreferencesRepository.updateStreamPreferences(any()) }
    assertEquals(
      current.copy(portraitGrid = portraitGrid, landscapeGrid = landscapeGrid),
      transform.captured(current)
    )
  }

  @Test
  fun `returns failure when the repository throws`() = runTest {
    val exception = RuntimeException("boom")
    coEvery { streamPreferencesRepository.updateStreamPreferences(any()) } throws exception

    val result = useCase(portraitGrid, landscapeGrid)

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }
}
