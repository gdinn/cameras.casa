package com.gdisys.cameras.flow

import com.gdisys.cameras.R
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.config
import com.gdisys.cameras.testing.robot.home
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

private val STREAMS = StreamDefaults.CAMERA_STREAM_URLS.take(2)

/** Scenario #5: a user whose configuration is already stored. */
@HiltAndroidTest
class ReturningUserFlowTest : FlowTest() {

  @Test
  fun storedConfigurationOpensHome() {
    seed.credentials()
    seed.streams(STREAMS)

    launchApp()

    // Config never enters the stack: the startup gate goes straight to Home.
    assertEquals(listOf(NavigationRoute.Home), backStackRoutes())
    home { assertShowsStreams(STREAMS) }
  }

  @Test
  fun credentialsWithoutUrlsShowEmptyHome() {
    seed.credentials()

    launchApp()

    assertEquals(listOf(NavigationRoute.Home), backStackRoutes())
    home { assertEmpty() }
    // Nothing to show, so the tunnel stays down.
    assertEquals(0, vpn.connectCalls.get())
  }

  @Test
  fun emptyHomeLeadsToConfig() {
    seed.credentials()
    launchApp()

    home { tapEmptyReconfigure() }

    assertEquals(listOf(NavigationRoute.Home, NavigationRoute.Config), backStackRoutes())
    config { assertHomeButtonText(R.string.config_screen_back_to_home) }
  }
}
