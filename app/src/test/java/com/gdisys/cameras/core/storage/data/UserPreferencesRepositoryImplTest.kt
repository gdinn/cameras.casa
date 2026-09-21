package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UserPreferencesRepositoryImplTest {

  private val dataStoreManager = mockk<DataStoreManager>()

  @Test
  fun `exposes the same flow instance as the data store manager`() {
    val flow = flowOf(UserPreferences())
    every { dataStoreManager.userPrefsState } returns flow

    val repository = UserPreferencesRepositoryImpl(dataStoreManager)

    assertSame(flow, repository.userPreferences)
  }

  @Test
  fun `updateUserPreferences replaces the stored value wholesale`() = runTest {
    every { dataStoreManager.userPrefsState } returns flowOf(UserPreferences())
    val transform = slot<(UserPreferences) -> UserPreferences>()
    coEvery { dataStoreManager.updateUserPreferences(capture(transform)) } returns Unit
    val repository = UserPreferencesRepositoryImpl(dataStoreManager)
    val preferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))

    repository.updateUserPreferences(preferences)

    // The repository contract takes a complete object, so its transform ignores what is stored.
    // The manager underneath still takes a transform, which is what makes partial writes possible.
    val stored = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "stale-key"))
    assertEquals(preferences, transform.captured(stored))
  }
}
