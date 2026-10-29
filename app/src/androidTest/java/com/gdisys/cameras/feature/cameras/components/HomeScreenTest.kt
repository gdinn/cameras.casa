package com.gdisys.cameras.feature.cameras.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.testing.setThemedContent
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.EglBase
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private val STREAMS = List(4) { "http://[fd00:20::cafe]:8889/cam_16$it" }
private val FIXED_GRID = GridPreferences(columns = 2, rows = 2, dynamicRows = false)
private val DYNAMIC_GRID = GridPreferences(columns = 2, rows = 2, dynamicRows = true)
private const val DRAG_STEPS = 10
private const val OVERSCROLL_SWIPES = 3

/**
 * The real `HomeScreen` and players with fake callbacks: the connections are recorded and never
 * made. Reordering is tested twice — through the accessibility actions, which is deterministic, and
 * through one real drag per grid mode, which only proves the touch wiring. The rest of the drag
 * logic is covered on the JVM by `PagedDragStateTest` and `StreamPagingTest`.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  // Not released: the renderers use it until the rule disposes the composition, after any @After.
  // The orchestrator runs each test in a new process anyway.
  private val eglBase = EglBase.create()

  private val reorders = CopyOnWriteArrayList<List<String>>()
  private val navigationsToConfig = AtomicInteger()

  @Test
  fun moveActionsReorderOnePlace() {
    setHomeScreen(FIXED_GRID)

    card(STREAMS[1]).performCustomAction(string(R.string.home_screen_move_stream_earlier))
    card(STREAMS[1]).performCustomAction(string(R.string.home_screen_move_stream_later))

    composeRule.runOnIdle {
      assertEquals(
        listOf(
          listOf(STREAMS[1], STREAMS[0], STREAMS[2], STREAMS[3]),
          listOf(STREAMS[0], STREAMS[2], STREAMS[1], STREAMS[3])
        ),
        reorders.toList()
      )
    }
  }

  @Test
  fun theEndsOfTheListOnlyMoveInward() {
    setHomeScreen(FIXED_GRID)

    card(STREAMS.first())
      .assert(hasCustomAction(string(R.string.home_screen_move_stream_later)))
      .assert(!hasCustomAction(string(R.string.home_screen_move_stream_earlier)))
    card(STREAMS.last())
      .assert(hasCustomAction(string(R.string.home_screen_move_stream_earlier)))
      .assert(!hasCustomAction(string(R.string.home_screen_move_stream_later)))
  }

  @Test
  fun reconfigureActionInDynamicModeNavigatesToConfig() {
    setHomeScreen(DYNAMIC_GRID)

    card(STREAMS[0]).performCustomAction(string(R.string.home_screen_reconfigure))

    composeRule.runOnIdle { assertEquals(1, navigationsToConfig.get()) }
  }

  @Test
  fun fixedGridHasNoReconfigureAction() {
    setHomeScreen(FIXED_GRID)

    card(STREAMS[0]).assert(!hasCustomAction(string(R.string.home_screen_reconfigure)))
  }

  @Test
  fun dragInTheFixedGridReordersOnDrop() {
    setHomeScreen(FIXED_GRID)

    dragOnto(STREAMS[0], STREAMS[1])

    composeRule.runOnIdle {
      assertEquals(listOf(listOf(STREAMS[1], STREAMS[0], STREAMS[2], STREAMS[3])), reorders.toList())
    }
  }

  @Test
  fun dragInTheDynamicGridReordersOnDrop() {
    setHomeScreen(DYNAMIC_GRID)

    dragOnto(STREAMS[0], STREAMS[1])

    composeRule.runOnIdle {
      assertEquals(listOf(listOf(STREAMS[1], STREAMS[0], STREAMS[2], STREAMS[3])), reorders.toList())
    }
  }

  /** With a single stream the grid cannot scroll, so every swipe up counts as overscroll. */
  @Test
  fun overscrollingTheDynamicGridRevealsReconfigure() {
    setHomeScreen(DYNAMIC_GRID, streams = STREAMS.take(1))
    val reconfigure = string(R.string.home_screen_reconfigure)
    composeRule.onNodeWithText(reconfigure).assertDoesNotExist()

    repeat(OVERSCROLL_SWIPES) {
      composeRule.onRoot().performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.1f) }
    }

    composeRule.onNodeWithText(reconfigure).assertIsDisplayed().performClick()
    composeRule.runOnIdle { assertEquals(1, navigationsToConfig.get()) }
  }

  private fun setHomeScreen(grid: GridPreferences, streams: List<String> = STREAMS) {
    composeRule.setThemedContent {
      HomeScreen(
        streams = streams,
        focusedStream = null,
        grid = grid,
        orientation = StreamOrientation.PORTRAIT,
        failedStreams = emptySet(),
        eglBase = eglBase,
        onConnectStream = { _, _ -> },
        onDisconnectStream = {},
        onFocusStream = {},
        onClearFocusedStream = {},
        onStreamsReordered = { reorders += it },
        onNavigateToConfig = { navigationsToConfig.incrementAndGet() }
      )
    }
  }

  private fun card(url: String) = composeRule.onNodeWithTag(HomeTestTags.streamCard(url))

  private fun centerOf(node: SemanticsNodeInteraction): Offset =
    node.fetchSemanticsNode().boundsInRoot.center

  /**
   * Presses [from]'s handle and moves the finger by the distance between the two cards' centers:
   * both grids move the dragged item by the finger's travel, so the item lands on [onto].
   */
  private fun dragOnto(from: String, onto: String) {
    val travel = centerOf(card(onto)) - centerOf(card(from))
    // The card is clickable, so it merges the handle into its own node.
    composeRule.onNodeWithTag(HomeTestTags.dragHandle(from), useUnmergedTree = true).performTouchInput {
      down(center)
      repeat(DRAG_STEPS) { moveBy(travel / DRAG_STEPS.toFloat()) }
      up()
    }
  }

  private fun hasCustomAction(label: String) = SemanticsMatcher("has custom action \"$label\"") { node ->
    node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
  }

  private fun SemanticsNodeInteraction.performCustomAction(label: String) {
    val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
    composeRule.runOnUiThread { action.action() }
  }
}
