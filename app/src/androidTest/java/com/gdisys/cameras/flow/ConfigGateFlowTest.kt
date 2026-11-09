package com.gdisys.cameras.flow

import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.VerificationModes.times
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.testing.FakeVpnPermissionRepository
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.QrFixture
import com.gdisys.cameras.testing.robot.ConfigStep
import com.gdisys.cameras.testing.robot.config
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private const val CAM_1 = "http://10.0.0.1:8889/cam_1/whep"

/**
 * Scenario #4: Config keeps the user until it is complete, and Home is reached only through its
 * button. Also the VPN consent step, answered by a stubbed dialog.
 */
@HiltAndroidTest
class ConfigGateFlowTest : FlowTest() {

  private val firstRunStack = listOf(NavigationRoute.Loading, NavigationRoute.Config)

  @Test
  fun homeButtonStaysWhileRequirementsAreMissing() {
    launchApp()

    config { tapHome() }

    assertEquals(firstRunStack, backStackRoutes())
  }

  @Test
  fun backIsConsumedWhileRequirementsAreMissing() {
    launchApp()

    pressBack()

    assertFalse(composeRule.activity.isFinishing)
    assertEquals(firstRunStack, backStackRoutes())
  }

  @Test
  fun backIsConsumedWithoutHomeBehindEvenWhenComplete() {
    // Credentials come from the QR after launch: seeded, they would send startup straight to Home.
    seed.streams(listOf(CAM_1))
    vpnConsent.granted = true
    launchApp()
    injectQr(QrFixture.VALID.read())
    config { assertStep(ConfigStep.QR_CODE, ConfigButtonState.Done) }

    pressBack()

    assertFalse(composeRule.activity.isFinishing)
    assertEquals(firstRunStack, backStackRoutes())
  }

  @Test
  fun deniedVpnConsentKeepsStepPending() {
    vpnConsent.stubConsentDialog(accept = false)
    launchApp()

    config { tapStep(ConfigStep.VPN) }

    intended(hasAction(FakeVpnPermissionRepository.CONSENT_ACTION))
    config { assertStep(ConfigStep.VPN, ConfigButtonState.Ready) }
    assertFalse(vpnConsent.granted)
  }

  @Test
  fun grantedVpnConsentSkipsTheDialog() {
    vpnConsent.granted = true
    launchApp()
    config { assertStep(ConfigStep.VPN, ConfigButtonState.Done) }

    config { tapStep(ConfigStep.VPN) }

    // Not assertNoUnverifiedIntents: the IntentsRule also records the Intent that opened the test
    // activity.
    intended(hasAction(FakeVpnPermissionRepository.CONSENT_ACTION), times(0))
  }
}
