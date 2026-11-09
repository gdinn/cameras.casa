package com.gdisys.cameras.flow

import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.ConfigStep
import com.gdisys.cameras.testing.robot.config
import com.gdisys.cameras.testing.robot.home
import com.gdisys.cameras.testing.robot.streamUrls
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Three streams fit on one screen in either grid, so every card is composed and can be measured.
private val STREAMS = StreamDefaults.CAMERA_STREAM_URLS.take(3)
private val LANDSCAPE_ORDER = STREAMS.reversed()

private val ONE_COLUMN = GridPreferences(columns = 1, rows = 1, dynamicRows = true)
private val THREE_COLUMNS = GridPreferences(columns = 3, rows = 1, dynamicRows = true)

/**
 * Scenario #7: each orientation has its own grid and order, and the grid saved on the URLs screen
 * is the one Home uses. The activity handles rotation itself, so this covers a configuration
 * change, not a recreation.
 */
@HiltAndroidTest
class GridOrientationFlowTest : FlowTest() {

  @Before
  fun seedOrientations() {
    seed.credentials()
    seed.streams(
      STREAMS,
      portraitGrid = ONE_COLUMN,
      landscapeGrid = THREE_COLUMNS,
      landscapeOrder = LANDSCAPE_ORDER
    )
  }

  // The requested orientation outlives a failed test's assertions: put the screen back.
  @After
  fun restorePortrait() {
    rotateTo(StreamOrientation.PORTRAIT)
  }

  @Test
  fun portraitUsesPortraitGrid() {
    launchApp()

    home { assertGrid(columns = 1, order = STREAMS) }
  }

  @Test
  fun rotatingSwitchesToLandscapeGrid() {
    launchApp()

    rotateTo(StreamOrientation.LANDSCAPE)
    home { assertGrid(columns = 3, order = LANDSCAPE_ORDER) }

    rotateTo(StreamOrientation.PORTRAIT)
    home { assertGrid(columns = 1, order = STREAMS) }
  }

  @Test
  fun savedGridIsAppliedOnHome() {
    // With the consent granted the configuration is complete, so "Back to Home" is allowed.
    vpnConsent.granted = true
    launchApp()

    home { reconfigureFrom(STREAMS.first()) }
    config { tapStep(ConfigStep.STREAM_URLS) }
    streamUrls {
      setGridColumns(StreamOrientation.PORTRAIT, "2")
      saveGrid()
      back()
    }
    config { tapHome() }

    assertEquals(listOf(NavigationRoute.Home), backStackRoutes())
    assertEquals(2, storedStreamPreferences().portraitGrid.columns)
    home { assertGrid(columns = 2, order = STREAMS) }
  }
}
