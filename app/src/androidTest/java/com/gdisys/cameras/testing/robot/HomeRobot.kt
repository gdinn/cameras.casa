package com.gdisys.cameras.testing.robot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.gdisys.cameras.feature.cameras.components.HomeTestTags
import com.gdisys.cameras.testing.FlowTest

/** What a user sees on Home. */
class HomeRobot(private val composeRule: ComposeTestRule) {

  /**
   * Every one of [urls] has a card in the grid. The grid is lazy, so each card is scrolled into
   * view first: only the visible ones are composed (and connected).
   */
  fun assertShowsStreams(urls: List<String>) {
    urls.forEach { url ->
      composeRule.onNodeWithTag(HomeTestTags.STREAM_GRID).performScrollToNode(hasTestTag(HomeTestTags.streamCard(url)))
      composeRule.onNodeWithTag(HomeTestTags.streamCard(url)).assertIsDisplayed()
    }
  }

  /** No URLs stored: Home shows the empty screen and its shortcut to Config. */
  fun assertEmpty() {
    composeRule.onNodeWithTag(HomeTestTags.EMPTY_STREAMS_RECONFIGURE_BUTTON).assertIsDisplayed()
  }

  fun tapEmptyReconfigure() {
    composeRule.onNodeWithTag(HomeTestTags.EMPTY_STREAMS_RECONFIGURE_BUTTON).performClick()
    composeRule.waitForIdle()
  }
}

/** Runs [block] against Home in the running app. */
fun FlowTest.home(block: HomeRobot.() -> Unit) = HomeRobot(composeRule).block()
