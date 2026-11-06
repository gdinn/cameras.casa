package com.gdisys.cameras.testing.robot

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.gdisys.cameras.feature.cameras.components.HomeTestTags
import com.gdisys.cameras.feature.cameras.components.StreamPlaybackState
import com.gdisys.cameras.feature.cameras.components.StreamPlaybackStateKey
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

  /**
   * The player of [url] is in [state], read from the semantics it exposes (the same contract the
   * real-environment smoke reads). The fake connections never deliver a frame, so a stream that did
   * not fail stays `CONNECTING`. The card is clickable, so it merges the player's semantics into
   * its own: the player is only found in the unmerged tree.
   */
  fun assertPlayback(url: String, state: StreamPlaybackState) {
    composeRule.onNodeWithTag(HomeTestTags.streamPlayer(url), useUnmergedTree = true)
      .assert(SemanticsMatcher.expectValue(StreamPlaybackStateKey, state))
  }

  /** The "Try again" button on the failed card of [url]. */
  fun tapRetry(url: String) {
    composeRule.onNode(retryButtonOf(url)).performClick()
    composeRule.waitForIdle()
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

// The overlay tags are the same on every card, so the button is told apart by the card around it.
private fun retryButtonOf(url: String): SemanticsMatcher =
  hasTestTag(HomeTestTags.STREAM_ERROR_RETRY_BUTTON) and hasAnyAncestor(hasTestTag(HomeTestTags.streamCard(url)))

/** Runs [block] against Home in the running app. */
fun FlowTest.home(block: HomeRobot.() -> Unit) = HomeRobot(composeRule).block()
