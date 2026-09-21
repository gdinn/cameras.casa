package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class StreamPreferencesRepositoryImplTest {

  private val dataStoreManager = mockk<StreamPreferencesDataStoreManager>()

  @Test
  fun `forwards every emission of the data store manager`() = runTest {
    val stored = StreamPreferences(streamUrls = listOf("a"))
    every { dataStoreManager.streamPrefsState } returns flowOf(StreamPreferences(), stored)

    val result = StreamPreferencesRepositoryImpl(dataStoreManager).streamPreferences.toList()

    assertEquals(listOf(StreamPreferences(), stored), result)
  }

  @Test
  fun `rethrows read failures so the use case decides what to emit`() = runTest {
    val failure = IllegalStateException("corrupt storage")
    every { dataStoreManager.streamPrefsState } returns flow { throw failure }

    try {
      StreamPreferencesRepositoryImpl(dataStoreManager).streamPreferences.toList()
      fail("expected the read failure to propagate")
    } catch (e: IllegalStateException) {
      assertSame(failure, e)
    }
  }

  @Test
  fun `updateStreamPreferences forwards the transform to the data store manager`() = runTest {
    every { dataStoreManager.streamPrefsState } returns flowOf(StreamPreferences())
    val transform = slot<(StreamPreferences) -> StreamPreferences>()
    coEvery { dataStoreManager.updateStreamPreferences(capture(transform)) } returns Unit
    val repository = StreamPreferencesRepositoryImpl(dataStoreManager)

    repository.updateStreamPreferences { it.copy(streamUrls = listOf("a")) }

    assertEquals(
      StreamPreferences(streamUrls = listOf("a")),
      transform.captured(StreamPreferences())
    )
  }
}
