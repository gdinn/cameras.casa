package com.gdisys.cameras.flow

import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.feature.cameras.components.StreamPlaybackState
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.home
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private val FAILING = StreamDefaults.CAMERA_STREAM_URLS[0]
private val WORKING = StreamDefaults.CAMERA_STREAM_URLS[1]

// Two columns, so both cards are on screen (and connected) at once in the lazy grid.
private val TWO_COLUMNS = GridPreferences(columns = 2, rows = 1, dynamicRows = true)

/** Scenario #10: one stream failing to connect, while the tunnel is up. */
@HiltAndroidTest
class StreamFailureFlowTest : FlowTest() {

  @Before
  fun failOneStream() {
    seed.credentials()
    seed.streams(listOf(FAILING, WORKING), portraitGrid = TWO_COLUMNS, landscapeGrid = TWO_COLUMNS)
    streams.failingUrls += FAILING
    launchApp()
  }

  @Test
  fun oneFailingStreamKeepsTheOthers() {
    assertEquals(listOf(NavigationRoute.Home), backStackRoutes())
    home {
      assertPlayback(FAILING, StreamPlaybackState.FAILED)
      assertPlayback(WORKING, StreamPlaybackState.CONNECTING)
    }
    assertEquals(setOf(WORKING), streams.connected)
  }

  @Test
  fun retryConnectsTheStreamOnceItRecovers() {
    home { assertPlayback(FAILING, StreamPlaybackState.FAILED) }
    streams.failingUrls -= FAILING

    home {
      tapRetry(FAILING)
      assertPlayback(FAILING, StreamPlaybackState.CONNECTING)
    }
    assertEquals(setOf(FAILING, WORKING), streams.connected)
  }
}
