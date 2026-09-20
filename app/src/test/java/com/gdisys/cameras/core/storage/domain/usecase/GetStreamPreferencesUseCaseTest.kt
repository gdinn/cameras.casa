package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetStreamPreferencesUseCaseTest {

  private val streamPreferencesRepository = mockk<StreamPreferencesRepository>()

  @Test
  fun `reconciles the orders of every emission`() = runTest {
    every { streamPreferencesRepository.streamPreferences } returns flowOf(
      StreamPreferences(streamUrls = listOf("a", "b")),
      StreamPreferences(
        streamUrls = listOf("a", "b"),
        portraitOrder = listOf("b", "removed"),
        landscapeOrder = listOf("b", "a")
      )
    )

    val result = GetStreamPreferencesUseCase(streamPreferencesRepository)().toList()

    assertEquals(listOf("a", "b"), result[0].portraitOrder)
    assertEquals(listOf("a", "b"), result[0].landscapeOrder)
    assertEquals(listOf("b", "a"), result[1].portraitOrder)
    assertEquals(listOf("b", "a"), result[1].landscapeOrder)
  }

  @Test
  fun `keeps the canonical urls and the grids as stored`() = runTest {
    val stored = StreamPreferences(
      streamUrls = listOf("a"),
      portraitOrder = listOf("a"),
      landscapeOrder = listOf("a"),
      portraitGrid = GridPreferences(columns = 2, rows = 3, dynamicRows = false),
      landscapeGrid = GridPreferences(columns = 4, rows = 1, dynamicRows = true)
    )
    every { streamPreferencesRepository.streamPreferences } returns flowOf(stored)

    val result = GetStreamPreferencesUseCase(streamPreferencesRepository)().first()

    assertEquals(stored, result)
  }
}
