package com.gdisys.cameras.testing.robot

import androidx.annotation.StringRes
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.feature.streamurls.components.StreamURLsTestTags
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.string

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

  /** Opens the add form, fills it and confirms. A rejected URL leaves the form open. */
  fun addUrl(port: String, streamName: String) {
    tapInList(StreamURLsTestTags.ADD_URL_BUTTON)
    // The form is the list's last item, so it may open below the fold.
    scrollTo(StreamURLsTestTags.PORT_INPUT)
    composeRule.onNodeWithTag(StreamURLsTestTags.PORT_INPUT).performTextInput(port)
    scrollTo(StreamURLsTestTags.STREAM_NAME_INPUT)
    composeRule.onNodeWithTag(StreamURLsTestTags.STREAM_NAME_INPUT).performTextInput(streamName)
    tapInList(StreamURLsTestTags.CONFIRM_ADD_URL_BUTTON)
    composeRule.waitForIdle()
  }

  /** Types [columns] into the column field of [orientation]'s grid. Not persisted until saved. */
  fun setGridColumns(orientation: StreamOrientation, columns: String) {
    val tag = StreamURLsTestTags.gridColumnsInput(orientation)
    scrollTo(tag)
    composeRule.onNodeWithTag(tag).performTextReplacement(columns)
  }

  /** Persists both grids, confirming the dialog. */
  fun saveGrid() {
    tapInList(StreamURLsTestTags.SAVE_GRID_BUTTON)
    confirmDialog()
  }

  fun assertAddFormOpen() {
    scrollTo(StreamURLsTestTags.ADD_URL_FORM)
    composeRule.onNodeWithTag(StreamURLsTestTags.ADD_URL_FORM).assertIsDisplayed()
  }

  /** The list is empty: its "no URLs" message is shown. */
  fun assertNoUrls() {
    scrollTo(StreamURLsTestTags.NO_URLS_MESSAGE)
    composeRule.onNodeWithTag(StreamURLsTestTags.NO_URLS_MESSAGE).assertIsDisplayed()
  }

  fun assertRow(url: String) {
    scrollTo(StreamURLsTestTags.urlRow(url))
    composeRule.onNodeWithTag(StreamURLsTestTags.urlRow(url)).assertIsDisplayed()
  }

  fun assertDialog(@StringRes titleRes: Int) {
    composeRule.onNode(isDialog()).assert(hasAnyDescendant(hasText(string(titleRes))))
  }

  fun confirmDialog() {
    composeRule.onNodeWithTag(StreamURLsTestTags.DIALOG_CONFIRM_BUTTON).performClick()
    composeRule.waitForIdle()
  }

  /** The top bar's back button, which takes the same path as the system back. */
  fun back() {
    composeRule.onNodeWithTag(StreamURLsTestTags.BACK_BUTTON).performClick()
    composeRule.waitForIdle()
  }

  // Everything but the top bar sits in a LazyColumn, where a node off screen is not composed.
  private fun tapInList(tag: String) {
    scrollTo(tag)
    composeRule.onNodeWithTag(tag).performClick()
  }

  private fun scrollTo(tag: String) {
    composeRule.onNodeWithTag(StreamURLsTestTags.LIST).performScrollToNode(hasTestTag(tag))
  }
}

/** Runs [block] against the stream URLs screen of the running app. */
fun FlowTest.streamUrls(block: StreamURLsRobot.() -> Unit) = StreamURLsRobot(composeRule).block()
