package com.gdisys.cameras.flow

import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.testing.DeviceState
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.QrFixture
import com.gdisys.cameras.testing.StreamEvent
import com.gdisys.cameras.testing.robot.ConfigStep
import com.gdisys.cameras.testing.robot.config
import com.gdisys.cameras.testing.robot.home
import com.gdisys.cameras.testing.robot.streamUrls
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scenario #2: a new user goes through the four steps of Config and reaches Home. */
@HiltAndroidTest
class OnboardingFlowTest : FlowTest() {

  @Test
  fun completeOnboardingReachesHome() {
    launchApp()

    // Granted after launch, so the click goes through the permission request: an already granted
    // permission returns its result without opening the system dialog.
    DeviceState.grantCameraPermission()
    config {
      tapStep(ConfigStep.CAMERA)
      assertStep(ConfigStep.CAMERA, ConfigButtonState.Done)
    }

    injectQr(QrFixture.VALID.read())
    config { assertStep(ConfigStep.QR_CODE, ConfigButtonState.Done) }

    vpnConsent.stubConsentDialog(accept = true)
    config {
      tapStep(ConfigStep.VPN)
      assertStep(ConfigStep.VPN, ConfigButtonState.Done)
      tapStep(ConfigStep.STREAM_URLS)
    }

    streamUrls {
      loadDefaults()
      saveUrls()
      back()
    }

    config {
      assertStep(ConfigStep.STREAM_URLS, ConfigButtonState.Done)
      tapHome()
    }

    assertEquals(listOf(NavigationRoute.Home), backStackRoutes())
    home { assertShowsStreams(StreamDefaults.CAMERA_STREAM_URLS) }
    assertEquals(1, vpn.connectCalls.get())
    assertEquals(1, vpnLifecycle.startCalls.get())
    // Cards scrolled out of view release their connection, so every URL was connected at some
    // point rather than all at once.
    assertTrue(streams.events.containsAll(StreamDefaults.CAMERA_STREAM_URLS.map(StreamEvent::Connect)))
  }
}
