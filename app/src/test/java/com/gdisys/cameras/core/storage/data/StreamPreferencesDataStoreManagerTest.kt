package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.DataStore
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

class StreamPreferencesDataStoreManagerTest {

  private val dataStore = mockk<DataStore<StreamPreferences>>()

  @Test
  fun `exposes the same flow as the data store`() {
    val flow = flowOf(StreamPreferences())
    every { dataStore.data } returns flow

    val manager = StreamPreferencesDataStoreManager(dataStore)

    assertSame(flow, manager.streamPrefsState)
  }

  @Test
  fun `updateStreamPreferences applies the transform to the stored value`() = runTest {
    every { dataStore.data } returns flowOf(StreamPreferences())
    val stored = StreamPreferences(streamUrls = listOf("a"))
    val transform = slot<suspend (StreamPreferences) -> StreamPreferences>()
    coEvery { dataStore.updateData(capture(transform)) } returns stored

    val manager = StreamPreferencesDataStoreManager(dataStore)
    manager.updateStreamPreferences { it.copy(streamUrls = it.streamUrls + "b") }

    assertEquals(
      StreamPreferences(streamUrls = listOf("a", "b")),
      transform.captured(stored)
    )
  }
}
