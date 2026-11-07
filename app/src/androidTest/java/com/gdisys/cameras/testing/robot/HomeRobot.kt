package com.gdisys.cameras.testing.robot

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.gdisys.cameras.R
import com.gdisys.cameras.feature.cameras.components.HomeTestTags
import com.gdisys.cameras.feature.cameras.components.StreamPlaybackState
import com.gdisys.cameras.feature.cameras.components.StreamPlaybackStateKey
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import kotlin.math.abs

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

  /**
   * The grid lays [order] out row by row in [columns] columns, read from where each card sits on
   * screen. Every card must be composed, so the seeded streams have to fit on one screen.
   */
  fun assertGrid(columns: Int, order: List<String>) {
    composeRule.waitForIdle()
    val bounds = order.associateWith { url ->
      composeRule.onNodeWithTag(HomeTestTags.streamCard(url)).fetchSemanticsNode().boundsInRoot
    }
    val onScreenOrder = order.sortedWith(compareBy({ bounds.getValue(it).top }, { bounds.getValue(it).left }))
    assertEquals("Cards in reading order", order, onScreenOrder)
    val firstRowTop = bounds.getValue(order.first()).top
    val firstRowSize = bounds.values.count { abs(it.top - firstRowTop) < 1f }
    assertEquals("Cards in the first row", minOf(columns, order.size), firstRowSize)
  }

  /** Opens Config through the "Reconfigure" accessibility action of [url]'s card. */
  fun reconfigureFrom(url: String) {
    val label = string(R.string.home_screen_reconfigure)
    val action = composeRule.onNodeWithTag(HomeTestTags.streamCard(url)).fetchSemanticsNode()
      .config[SemanticsActions.CustomActions].single { it.label == label }
    composeRule.runOnUiThread { action.action() }
    composeRule.waitForIdle()
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
