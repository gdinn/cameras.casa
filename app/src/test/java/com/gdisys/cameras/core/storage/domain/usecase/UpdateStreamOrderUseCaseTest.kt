package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
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

class UpdateStreamOrderUseCaseTest {

  private val streamPreferencesRepository = mockk<StreamPreferencesRepository>()
  private lateinit var useCase: UpdateStreamOrderUseCase

  private val current = StreamPreferences(
    streamUrls = listOf("a", "b"),
    portraitOrder = listOf("a", "b"),
    landscapeOrder = listOf("a", "b")
  )

  @Before
  fun setUp() {
    useCase = UpdateStreamOrderUseCase(streamPreferencesRepository)
  }

  @Test
  fun `writes only the portrait order`() = runTest {
    val transform = slot<(StreamPreferences) -> StreamPreferences>()
    coEvery { streamPreferencesRepository.updateStreamPreferences(capture(transform)) } returns Unit

    val result = useCase(StreamOrientation.PORTRAIT, listOf("b", "a"))

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { streamPreferencesRepository.updateStreamPreferences(any()) }
    assertEquals(
      current.copy(portraitOrder = listOf("b", "a")),
      transform.captured(current)
    )
  }

  @Test
  fun `writes only the landscape order`() = runTest {
    val transform = slot<(StreamPreferences) -> StreamPreferences>()
    coEvery { streamPreferencesRepository.updateStreamPreferences(capture(transform)) } returns Unit

    val result = useCase(StreamOrientation.LANDSCAPE, listOf("b", "a"))

    assertTrue(result.isSuccess)
    assertEquals(
      current.copy(landscapeOrder = listOf("b", "a")),
      transform.captured(current)
    )
  }

  @Test
  fun `returns failure when the repository throws`() = runTest {
    val exception = RuntimeException("boom")
    coEvery { streamPreferencesRepository.updateStreamPreferences(any()) } throws exception

    val result = useCase(StreamOrientation.PORTRAIT, listOf("a"))

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }
}
