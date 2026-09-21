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

class SaveStreamUrlsUseCaseTest {

  private val streamPreferencesRepository = mockk<StreamPreferencesRepository>()
  private lateinit var useCase: SaveStreamUrlsUseCase

  @Before
  fun setUp() {
    useCase = SaveStreamUrlsUseCase(streamPreferencesRepository)
  }

  @Test
  fun `overwrites the canonical urls and both orders`() = runTest {
    val transform = slot<(StreamPreferences) -> StreamPreferences>()
    coEvery { streamPreferencesRepository.updateStreamPreferences(capture(transform)) } returns Unit
    val current = StreamPreferences(
      streamUrls = listOf("old"),
      portraitOrder = listOf("old"),
      landscapeOrder = listOf("old")
    )

    val result = useCase(
      streamUrls = listOf("a", "b"),
      portraitOrder = listOf("b", "a"),
      landscapeOrder = listOf("a", "b")
    )

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { streamPreferencesRepository.updateStreamPreferences(any()) }
    assertEquals(
      StreamPreferences(
        streamUrls = listOf("a", "b"),
        portraitOrder = listOf("b", "a"),
        landscapeOrder = listOf("a", "b")
      ),
      transform.captured(current)
    )
  }

  @Test
  fun `preserves the grid preferences`() = runTest {
    val transform = slot<(StreamPreferences) -> StreamPreferences>()
    coEvery { streamPreferencesRepository.updateStreamPreferences(capture(transform)) } returns Unit
    val portraitGrid = GridPreferences(columns = 2, rows = 3, dynamicRows = false)
    val landscapeGrid = GridPreferences(columns = 4, rows = 1, dynamicRows = true)
    val current = StreamPreferences(portraitGrid = portraitGrid, landscapeGrid = landscapeGrid)

    useCase(listOf("a"), listOf("a"), listOf("a"))

    val updated = transform.captured(current)
    assertEquals(portraitGrid, updated.portraitGrid)
    assertEquals(landscapeGrid, updated.landscapeGrid)
  }

  @Test
  fun `returns failure when the repository throws`() = runTest {
    val exception = RuntimeException("boom")
    coEvery { streamPreferencesRepository.updateStreamPreferences(any()) } throws exception

    val result = useCase(listOf("a"), listOf("a"), listOf("a"))

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }
}
