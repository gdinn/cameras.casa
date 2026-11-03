package com.gdisys.cameras

import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.testing.FlowTest
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test for the shared composition root: the real navigation graph and Hilt ViewModels,
 * rendered in `HiltTestActivity` with a NavController the test owns (see [FlowTest] for what is
 * replaced). Each test gets new, empty stores, so startup always lands on Config.
 */
@HiltAndroidTest
class CamerasAppContentTest : FlowTest() {

  @Test
  fun firstRunLandsOnConfigOverLoading() {
    launchApp()

    // No waitUntil: the stores read on the idling IO dispatcher (TestDispatchersModule), so idle
    // means the credentials were read and the navigation happened. Loading stays below Config:
    // InitRoute navigates without popping itself.
    assertEquals(listOf(NavigationRoute.Loading, NavigationRoute.Config), backStackRoutes())
  }
}
