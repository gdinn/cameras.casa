package com.gdisys.cameras.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.gdisys.cameras.MainActivity
import com.gdisys.cameras.testing.robot.ConfigStep
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * Scenario #0: the real entry point. Every other flow test renders `CamerasAppContent` in
 * `HiltTestActivity`; this one opens `MainActivity` itself, with the `EglBase` Hilt injects into
 * it, so a break in the activity (its injection, its `setContent`) does not go unnoticed.
 */
@HiltAndroidTest
class MainActivitySmokeTest {

  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun realEntryPointOpensConfigOnFirstRun() {
    // The stores read on the idling IO dispatcher, so the first assertion waits for the startup
    // gate to land on Config.
    ConfigStep.entries.forEach { composeRule.onNodeWithTag(it.tag).assertIsDisplayed() }
  }
}
