package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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
  fun `updateUserPreferences delegates to the data store manager`() = runTest {
    every { dataStoreManager.userPrefsState } returns flowOf(UserPreferences())
    coEvery { dataStoreManager.updateUserPreferences(any()) } returns Unit
    val repository = UserPreferencesRepositoryImpl(dataStoreManager)
    val preferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))

    repository.updateUserPreferences(preferences)

    coVerify(exactly = 1) { dataStoreManager.updateUserPreferences(preferences) }
  }
}
