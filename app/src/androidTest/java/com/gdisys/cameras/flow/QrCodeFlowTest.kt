package com.gdisys.cameras.flow

import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.QrFixture
import com.gdisys.cameras.testing.robot.ConfigStep
import com.gdisys.cameras.testing.robot.config
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Scenario #3: a scanned QR reaching the Config screen. The payload comes in through the Config
 * entry's `savedStateHandle`, as the scanner hands it over; the scanner screen is not part of it.
 */
@HiltAndroidTest
class QrCodeFlowTest : FlowTest() {

  @Test
  fun validQrCompletesTheStep() {
    launchApp()

    injectQr(QrFixture.VALID.read())

    config { assertStep(ConfigStep.QR_CODE, ConfigButtonState.Done) }
    assertNotEquals(UserPreferences(), storedUserPreferences())
    // InitViewModel stays on the stack and sees the new credentials: the app must still not leave
    // Config on its own.
    assertEquals(listOf(NavigationRoute.Loading, NavigationRoute.Config), backStackRoutes())
  }

  @Test
  fun qrWithMissingFieldIsRejected() = assertRejected(QrFixture.INCOMPLETE)

  @Test
  fun qrWithWrongTypeIsRejected() = assertRejected(QrFixture.WRONG_TYPE)

  @Test
  fun qrWithUnknownShapeIsRejected() = assertRejected(QrFixture.UNKNOWN_SHAPE)

  /** Documents current behavior: a rejected QR also wipes the credentials a valid one stored. */
  @Test
  fun invalidQrAfterValidOneClearsCredentials() {
    launchApp()
    injectQr(QrFixture.VALID.read())

    injectQr(QrFixture.INCOMPLETE.read())

    config { assertStep(ConfigStep.QR_CODE, ConfigButtonState.Error) }
    assertEquals(UserPreferences(), storedUserPreferences())
  }

  private fun assertRejected(fixture: QrFixture) {
    launchApp()

    injectQr(fixture.read())

    config { assertStep(ConfigStep.QR_CODE, ConfigButtonState.Error) }
    assertEquals(UserPreferences(), storedUserPreferences())
    assertEquals(listOf(NavigationRoute.Loading, NavigationRoute.Config), backStackRoutes())
  }
}
