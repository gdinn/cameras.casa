package com.gdisys.cameras.flow

import com.gdisys.cameras.R
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.config
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Scenario #9: a tunnel that fails to come up sends the user back to Config. */
@HiltAndroidTest
class VpnFailureFlowTest : FlowTest() {

  @Before
  fun failTheTunnel() {
    seed.credentials()
    seed.streams(StreamDefaults.CAMERA_STREAM_URLS.take(1))
    // With the consent granted the configuration is complete, so "Back to Home" is allowed.
    vpnConsent.granted = true
    vpn.connectFailure = IOException("Fake tunnel failure")
  }

  @Test
  fun failedConnectionReturnsToConfig() {
    launchApp()

    assertEquals(listOf(NavigationRoute.Home, NavigationRoute.Config), backStackRoutes())
    config { assertHomeButtonText(R.string.config_screen_back_to_home) }
    assertEquals(1, vpn.connectCalls.get())
    assertEquals(0, vpnLifecycle.startCalls.get())
  }

  @Test
  fun retryingFromConfigDoesNotStackDestinations() {
    launchApp()

    config { tapHome() }

    // Back on Home the tunnel fails again, and Config opens over the same Home.
    assertEquals(2, vpn.connectCalls.get())
    assertEquals(listOf(NavigationRoute.Home, NavigationRoute.Config), backStackRoutes())
  }
}
