package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StreamPreferencesRepositoryImplTest {

  private val dataStoreManager = mockk<StreamPreferencesDataStoreManager>()

  @Test
  fun `exposes the same flow instance as the data store manager`() {
    val flow = flowOf(StreamPreferences())
    every { dataStoreManager.streamPrefsState } returns flow

    val repository = StreamPreferencesRepositoryImpl(dataStoreManager)

    assertSame(flow, repository.streamPreferences)
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
