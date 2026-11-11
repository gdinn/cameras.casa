package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MarkNotificationPermissionRequestedUseCaseTest {

  private val repository = mockk<StreamPreferencesRepository>()

  @Test
  fun `only the flag changes`() = runTest {
    val stored = StreamPreferences(streamUrls = listOf("http://[fd00:20::cafe]:8889/cam_10"))
    var written: StreamPreferences? = null
    coEvery { repository.updateStreamPreferences(any()) } answers {
      written = firstArg<(StreamPreferences) -> StreamPreferences>()(stored)
    }

    assertTrue(MarkNotificationPermissionRequestedUseCase(repository)().isSuccess)
    assertEquals(stored.copy(notificationPermissionRequested = true), written)
  }

  @Test
  fun `a write failure becomes a failed result`() = runTest {
    coEvery { repository.updateStreamPreferences(any()) } throws IOException("disk full")

    assertTrue(MarkNotificationPermissionRequestedUseCase(repository)().isFailure)
  }
}
