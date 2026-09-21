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
  fun `updateUserPreferences applies the given transform to the stored value`() = runTest {
    every { dataStore.data } returns flowOf(UserPreferences())
    val newPreferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))
    val transform = slot<suspend (UserPreferences) -> UserPreferences>()
    coEvery { dataStore.updateData(capture(transform)) } returns newPreferences

    val manager = DataStoreManager(dataStore)
    manager.updateUserPreferences { newPreferences }

    assertEquals(newPreferences, transform.captured(UserPreferences()))
  }

  @Test
  fun `updateUserPreferences hands the persisted value to the transform`() = runTest {
    every { dataStore.data } returns flowOf(UserPreferences())
    val persisted = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "persisted-key"))
    val transform = slot<suspend (UserPreferences) -> UserPreferences>()
    coEvery { dataStore.updateData(capture(transform)) } returns persisted

    val manager = DataStoreManager(dataStore)
    manager.updateUserPreferences { current ->
      current.copy(vpnConfigTokens = current.vpnConfigTokens?.copy(iAddr = "10.0.0.2"))
    }

    // The point of the transform overload: a caller can rewrite one field and keep the rest,
    // instead of having to supply a complete object and clobber whatever it did not know about.
    assertEquals(
      UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "persisted-key", iAddr = "10.0.0.2")),
      transform.captured(persisted)
    )
  }
}
