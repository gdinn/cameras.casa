package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.DataStore
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

class DataStoreManagerTest {

  private val dataStore = mockk<DataStore<UserPreferences>>()

  @Test
  fun `exposes the same flow as the data store`() {
    val flow = flowOf(UserPreferences())
    every { dataStore.data } returns flow

    val manager = DataStoreManager(dataStore)

    assertSame(flow, manager.userPrefsState)
  }

  @Test
  fun `updateUserPreferences replaces the stored value with the given preferences`() = runTest {
    every { dataStore.data } returns flowOf(UserPreferences())
    val newPreferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))
    val transform = slot<suspend (UserPreferences) -> UserPreferences>()
    coEvery { dataStore.updateData(capture(transform)) } returns newPreferences

    val manager = DataStoreManager(dataStore)
    manager.updateUserPreferences(newPreferences)

    assertEquals(newPreferences, transform.captured(UserPreferences()))
  }
}
