package com.gdisys.cameras.flow

import com.gdisys.cameras.R
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.config
import com.gdisys.cameras.testing.robot.home
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private val STREAMS = StreamDefaults.CAMERA_STREAM_URLS.take(2)

// The floating "Reconfigure" button only exists in the fixed grid.
private val FIXED_GRID = GridPreferences(columns = 2, rows = 1, dynamicRows = false)

// Two dynamic columns: both streams fit in one row, so the grid cannot scroll and every swipe up is
// overscroll.
private val DYNAMIC_GRID = GridPreferences(columns = 2, rows = 1, dynamicRows = true)

/**
 * Scenario #11: Home's two ways into Config, the way back, and what the round trip does to the
 * tunnel. Opening Config pauses Home's back stack entry, so the tunnel goes down and comes back up.
 */
@HiltAndroidTest
class ReconfigureFlowTest : FlowTest() {

  @Before
  fun seedCredentials() {
    seed.credentials()
    // With the consent granted the configuration is complete, so "Back to Home" is allowed.
    vpnConsent.granted = true
  }

  @Test
  fun fixedGridButtonOpensConfig() {
    openHome(FIXED_GRID)

    home { tapReconfigureButton() }

    assertConfigOverHome()
  }

  @Test
  fun overscrollRevealsReconfigure() {
    openHome(DYNAMIC_GRID)

    home { reconfigureByOverscroll() }

    assertConfigOverHome()
  }

  @Test
  fun returningToHomeDoesNotStackIt() {
    openHome(FIXED_GRID)

    home { tapReconfigureButton() }
    config { tapHome() }
    assertEquals(listOf(NavigationRoute.Home), backStackRoutes())

    home { tapReconfigureButton() }
    pressBack()
    assertEquals(listOf(NavigationRoute.Home), backStackRoutes())
    home { assertShowsStreams(STREAMS) }
  }

  @Test
  fun reconfigureCyclesTheTunnel() {
    openHome(FIXED_GRID)
    assertEquals(1, vpn.connectCalls.get())
    assertEquals(1, vpnLifecycle.startCalls.get())

    home { tapReconfigureButton() }

    assertEquals(1, vpn.disconnectCalls.get())
    assertEquals(1, vpnLifecycle.stopCalls.get())
    assertEquals(emptySet<String>(), streams.connected)

    config { tapHome() }

    assertEquals(2, vpn.connectCalls.get())
    assertEquals(2, vpnLifecycle.startCalls.get())
    assertEquals(STREAMS.toSet(), streams.connected)
  }

  private fun openHome(grid: GridPreferences) {
    seed.streams(STREAMS, portraitGrid = grid, landscapeGrid = grid)
    launchApp()
    home { assertShowsStreams(STREAMS) }
  }

  private fun assertConfigOverHome() {
    assertEquals(listOf(NavigationRoute.Home, NavigationRoute.Config), backStackRoutes())
    config { assertHomeButtonText(R.string.config_screen_back_to_home) }
  }
}
