package com.gdisys.cameras.flow

import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.home
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Three streams fit on one screen in either grid, so every card is composed and can be measured.
private val STREAMS = StreamDefaults.CAMERA_STREAM_URLS.take(3)
private val LANDSCAPE_ORDER = STREAMS.reversed()

// The first stream dragged onto the second swaps them.
private val REORDERED = listOf(STREAMS[1], STREAMS[0], STREAMS[2])

private val TWO_COLUMNS = GridPreferences(columns = 2, rows = 1, dynamicRows = true)
private val THREE_COLUMNS = GridPreferences(columns = 3, rows = 1, dynamicRows = true)

/**
 * Scenario #12: dragging a card in the dynamic grid reorders the streams of the current orientation
 * only, and the new order is persisted.
 */
@HiltAndroidTest
class ReorderStreamsFlowTest : FlowTest() {

  @Before
  fun openHome() {
    seed.credentials()
    seed.streams(
      STREAMS,
      portraitGrid = TWO_COLUMNS,
      landscapeGrid = THREE_COLUMNS,
      landscapeOrder = LANDSCAPE_ORDER
    )
    launchApp()
    home { assertGrid(columns = 2, order = STREAMS) }
  }

  // The requested orientation outlives a failed test's assertions: put the screen back.
  @After
  fun restorePortrait() {
    rotateTo(StreamOrientation.PORTRAIT)
  }

  @Test
  fun draggingAStreamPersistsTheNewOrder() {
    home {
      dragOnto(STREAMS[0], STREAMS[1])
      assertGrid(columns = 2, order = REORDERED)
    }

    val stored = storedStreamPreferences()
    assertEquals(REORDERED, stored.portraitOrder)
    // The set of URLs is not touched: only the order of this orientation.
    assertEquals(STREAMS, stored.streamUrls)
  }

  @Test
  fun reorderingKeepsTheOtherOrientation() {
    home { dragOnto(STREAMS[0], STREAMS[1]) }

    assertEquals(LANDSCAPE_ORDER, storedStreamPreferences().landscapeOrder)
    rotateTo(StreamOrientation.LANDSCAPE)
    home { assertGrid(columns = 3, order = LANDSCAPE_ORDER) }
  }
}
