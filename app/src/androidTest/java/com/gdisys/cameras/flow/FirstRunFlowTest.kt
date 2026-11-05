package com.gdisys.cameras.flow

import com.gdisys.cameras.R
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.config
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Scenario #1: the first run, with empty stores, no camera permission and no VPN consent (the
 * starting state of every flow test).
 */
@HiltAndroidTest
class FirstRunFlowTest : FlowTest() {

  @Test
  fun firstRunLandsOnConfig() {
    launchApp()

    // No waitUntil: the stores read on the idling IO dispatcher, so idle means the startup gate
    // decided. Loading stays below Config: InitRoute navigates without popping itself.
    assertEquals(listOf(NavigationRoute.Loading, NavigationRoute.Config), backStackRoutes())
  }

  @Test
  fun firstRunStepsStartPending() {
    launchApp()

    config {
      assertEveryStep(ConfigButtonState.Ready)
      assertHomeButtonText(R.string.config_screen_navigate_to_home)
    }
  }
}
