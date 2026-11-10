package com.gdisys.cameras.systemdialog

import android.net.VpnService
import androidx.test.platform.app.InstrumentationRegistry
import com.gdisys.cameras.R
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.SystemDialogs
import com.gdisys.cameras.testing.robot.ConfigStep
import com.gdisys.cameras.testing.robot.config
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The real VPN consent dialog, answered through UI Automator. The suite fakes the consent
 * (`TestPermissionModule`), so this class switches the fake to the production repository: tapping
 * the step then launches the Intent `VpnService.prepare` returns, as on a device.
 */
@HiltAndroidTest
class VpnConsentDialogTest : FlowTest() {

  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun askForConsent() {
    SystemDialogs.VpnConsent.reset()
    assertNotNull("The VPN consent should start ungranted", VpnService.prepare(context))
    vpnConsent.useSystemConsent = true
    launchApp()
  }

  // The app op outlives the test (and `pm clear`): leave the device as the next test expects.
  @After
  fun forgetConsent() {
    SystemDialogs.VpnConsent.reset()
  }

  @Test
  fun acceptingTheDialogCompletesTheStep() {
    config { tapStep(ConfigStep.VPN) }
    SystemDialogs.VpnConsent.accept()

    config { assertStep(ConfigStep.VPN, ConfigButtonState.Done) }
    // The first run also toasts that there are no credentials yet, so only the last toast is read.
    assertEquals(R.string.config_route_vpn_accepted, toasts.shown.last())
    assertNull("The system should now report the consent as given", VpnService.prepare(context))
  }

  @Test
  fun cancellingTheDialogKeepsTheStepReady() {
    config { tapStep(ConfigStep.VPN) }
    SystemDialogs.VpnConsent.cancel()

    config { assertStep(ConfigStep.VPN, ConfigButtonState.Ready) }
    assertEquals(R.string.config_route_vpn_denied, toasts.shown.last())
    assertNotNull(VpnService.prepare(context))
  }
}
