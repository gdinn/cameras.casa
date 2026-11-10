package com.gdisys.cameras.testing.robot

import androidx.annotation.StringRes
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gdisys.cameras.R
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.feature.config.components.ConfigTestTags
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.hasStateDescription
import com.gdisys.cameras.testing.string

/** The four steps of the Config screen, by the tag of their button. */
enum class ConfigStep(val tag: String) {
  CAMERA(ConfigTestTags.CAMERA_PERMISSION_STEP),
  QR_CODE(ConfigTestTags.QR_CODE_STEP),
  VPN(ConfigTestTags.VPN_PERMISSION_STEP),
  STREAM_URLS(ConfigTestTags.STREAM_URLS_STEP)
}

/**
 * What a user does and sees on the Config screen, so flow tests read as the user's steps. A step's
 * state is read from its `stateDescription`, the same text TalkBack speaks.
 */
class ConfigRobot(private val composeRule: ComposeTestRule) {

  fun tapStep(step: ConfigStep) {
    composeRule.onNodeWithTag(step.tag).performClick()
    composeRule.waitForIdle()
  }

  fun tapHome() {
    composeRule.onNodeWithTag(ConfigTestTags.NAVIGATE_TO_HOME_BUTTON).performScrollTo().performClick()
    composeRule.waitForIdle()
  }

  fun assertStep(step: ConfigStep, state: ConfigButtonState) {
    composeRule.onNodeWithTag(step.tag).assert(hasStateDescription(state.descriptionRes()))
  }

  fun assertEveryStep(state: ConfigButtonState) {
    ConfigStep.entries.forEach { assertStep(it, state) }
  }

  /** On a first run the button reads "Navigate to Home"; with Home below, "Back to Home". */
  fun assertHomeButtonText(@StringRes textRes: Int) {
    composeRule.onNodeWithTag(ConfigTestTags.NAVIGATE_TO_HOME_BUTTON).assertTextEquals(string(textRes))
  }
}

/** Runs [block] against the Config screen of the running app. */
fun FlowTest.config(block: ConfigRobot.() -> Unit) = ConfigRobot(composeRule).block()

// Mirrors the private mapping in ConfigurationStepButton, so a change there breaks these tests.
@StringRes
private fun ConfigButtonState.descriptionRes(): Int = when (this) {
  ConfigButtonState.Loading -> R.string.config_step_state_loading
  ConfigButtonState.Ready -> R.string.config_step_state_ready
  ConfigButtonState.Done -> R.string.config_step_state_done
  ConfigButtonState.Error -> R.string.config_step_state_error
}
