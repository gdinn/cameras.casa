package com.gdisys.cameras.feature.config.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.feature.config.ConfigUiState
import com.gdisys.cameras.testing.setThemedContent
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun eachStepExposesItsState() {
    composeRule.setConfigScreen(
      ConfigUiState(
        cameraPermissionButtonState = ConfigButtonState.Loading,
        qrCodeButtonState = ConfigButtonState.Ready,
        vpnPermissionButtonState = ConfigButtonState.Done,
        streamURLsButtonState = ConfigButtonState.Error
      )
    )

    composeRule.onNodeWithTag(ConfigTestTags.CAMERA_PERMISSION_STEP)
      .assert(hasStateDescription(R.string.config_step_state_loading))
    composeRule.onNodeWithTag(ConfigTestTags.QR_CODE_STEP)
      .assert(hasStateDescription(R.string.config_step_state_ready))
    composeRule.onNodeWithTag(ConfigTestTags.VPN_PERMISSION_STEP)
      .assert(hasStateDescription(R.string.config_step_state_done))
    composeRule.onNodeWithTag(ConfigTestTags.STREAM_URLS_STEP)
      .assert(hasStateDescription(R.string.config_step_state_error))
  }

  @Test
  fun cameraStepClickRequestsCameraPermission() {
    val events = composeRule.setConfigScreen(ConfigUiState())

    composeRule.onNodeWithTag(ConfigTestTags.CAMERA_PERMISSION_STEP).performClick()

    assertEquals(listOf("requestCameraPermission"), events.calls)
  }

  @Test
  fun qrCodeStepClickOpensScanner() {
    val events = composeRule.setConfigScreen(
      ConfigUiState(cameraPermissionButtonState = ConfigButtonState.Done)
    )

    composeRule.onNodeWithTag(ConfigTestTags.QR_CODE_STEP).performClick()

    assertEquals(listOf("showScanner"), events.calls)
  }

  @Test
  fun qrCodeStepStillClickableWithoutCameraPermission() {
    // `enabled` is visual only: the ViewModel answers this click with a toast explaining that the
    // camera permission is missing, so the screen must still forward it.
    val events = composeRule.setConfigScreen(
      ConfigUiState(cameraPermissionButtonState = ConfigButtonState.Ready)
    )

    composeRule.onNodeWithTag(ConfigTestTags.QR_CODE_STEP)
      .assertIsEnabled()
      .performClick()

    assertEquals(listOf("showScanner"), events.calls)
  }

  @Test
  fun vpnStepClickRequestsVpnPermission() {
    val events = composeRule.setConfigScreen(ConfigUiState())

    composeRule.onNodeWithTag(ConfigTestTags.VPN_PERMISSION_STEP).performClick()

    assertEquals(listOf("acceptVpnPermission"), events.calls)
  }

  @Test
  fun streamUrlsStepClickNavigatesToStreamUrls() {
    val events = composeRule.setConfigScreen(ConfigUiState())

    composeRule.onNodeWithTag(ConfigTestTags.STREAM_URLS_STEP).performClick()

    assertEquals(listOf("navigateToStreamURLs"), events.calls)
  }

  @Test
  fun homeButtonRequestsNavigation() {
    // Requirements are still missing here: the gate belongs to the ViewModel, not to the screen.
    val events = composeRule.setConfigScreen(ConfigUiState())

    composeRule.onNodeWithTag(ConfigTestTags.NAVIGATE_TO_HOME_BUTTON).performClick()

    assertEquals(listOf("navigateToHome"), events.calls)
  }

  @Test
  fun homeButtonSaysNavigateWhenThereIsNoHomeBehind() {
    composeRule.setConfigScreen(ConfigUiState(canNavigateBackToHome = false))

    composeRule.onNodeWithTag(ConfigTestTags.NAVIGATE_TO_HOME_BUTTON)
      .assertTextEquals(string(R.string.config_screen_navigate_to_home))
  }

  @Test
  fun homeButtonSaysBackWhenHomeIsInTheBackStack() {
    composeRule.setConfigScreen(ConfigUiState(canNavigateBackToHome = true))

    composeRule.onNodeWithTag(ConfigTestTags.NAVIGATE_TO_HOME_BUTTON)
      .assertTextEquals(string(R.string.config_screen_back_to_home))
  }

  @Test
  fun stepsShowTitleAndSubtitle() {
    composeRule.setConfigScreen(ConfigUiState())

    listOf(
      Triple(
        ConfigTestTags.CAMERA_PERMISSION_STEP,
        R.string.config_screen_camera_permission_title,
        R.string.config_screen_camera_permission_subtitle
      ),
      Triple(
        ConfigTestTags.QR_CODE_STEP,
        R.string.config_screen_load_configuration_title,
        R.string.config_screen_load_configuration_subtitle
      ),
      Triple(
        ConfigTestTags.VPN_PERMISSION_STEP,
        R.string.config_screen_vpn_permission_title,
        R.string.config_screen_vpn_permission_subtitle
      ),
      Triple(
        ConfigTestTags.STREAM_URLS_STEP,
        R.string.config_screen_stream_urls_title,
        R.string.config_screen_stream_urls_subtitle
      )
    ).forEach { (tag, title, subtitle) ->
      composeRule.onNodeWithTag(tag)
        .assertTextContains(string(title))
        .assertTextContains(string(subtitle))
    }
  }

  private fun hasStateDescription(resId: Int): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, string(resId))

  /** Records every callback ConfigScreen fires, in order. */
  private class ConfigScreenEvents {
    val calls = mutableListOf<String>()
  }

  private fun ComposeContentTestRule.setConfigScreen(uiState: ConfigUiState): ConfigScreenEvents {
    val events = ConfigScreenEvents()
    setThemedContent {
      ConfigScreen(
        uiState = uiState,
        onShowScanner = { events.calls += "showScanner" },
        acceptVpnPermission = { events.calls += "acceptVpnPermission" },
        onRequestCameraPermission = { events.calls += "requestCameraPermission" },
        onNavigateToStreamURLs = { events.calls += "navigateToStreamURLs" },
        onNavigateToHome = { events.calls += "navigateToHome" }
      )
    }
    return events
  }
}
