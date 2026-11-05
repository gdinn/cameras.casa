package com.gdisys.cameras.testing.robot

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.gdisys.cameras.feature.streamurls.components.StreamURLsTestTags
import com.gdisys.cameras.testing.FlowTest

/** What a user does on the stream URLs screen. */
class StreamURLsRobot(private val composeRule: ComposeTestRule) {

  /** Replaces the list with the factory URLs, confirming the dialog. Not persisted until saved. */
  fun loadDefaults() {
    tapInList(StreamURLsTestTags.LOAD_DEFAULTS_BUTTON)
    confirmDialog()
  }

  /** Persists the URL list, confirming the dialog. */
  fun saveUrls() {
    tapInList(StreamURLsTestTags.SAVE_URLS_BUTTON)
    confirmDialog()
  }

  /** The top bar's back button, which takes the same path as the system back. */
  fun back() {
    composeRule.onNodeWithTag(StreamURLsTestTags.BACK_BUTTON).performClick()
    composeRule.waitForIdle()
  }

  // The buttons sit at the end of a LazyColumn: once the list has URLs they may not be composed.
  private fun tapInList(tag: String) {
    composeRule.onNodeWithTag(StreamURLsTestTags.LIST).performScrollToNode(hasTestTag(tag))
    composeRule.onNodeWithTag(tag).performClick()
  }

  private fun confirmDialog() {
    composeRule.onNodeWithTag(StreamURLsTestTags.DIALOG_CONFIRM_BUTTON).performClick()
    composeRule.waitForIdle()
  }
}

/** Runs [block] against the stream URLs screen of the running app. */
fun FlowTest.streamUrls(block: StreamURLsRobot.() -> Unit) = StreamURLsRobot(composeRule).block()
