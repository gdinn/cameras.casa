package com.gdisys.cameras.flow

import androidx.lifecycle.Lifecycle
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.home
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private val STREAMS = StreamDefaults.CAMERA_STREAM_URLS.take(2)

// Two columns, so both cards are on screen (and connected) at once in the lazy grid.
private val TWO_COLUMNS = GridPreferences(columns = 2, rows = 1, dynamicRows = true)

/** Scenario #8: the tunnel and the streams follow Home's lifecycle. */
@HiltAndroidTest
class VpnLifecycleFlowTest : FlowTest() {

  @Before
  fun openHome() {
    seed.credentials()
    seed.streams(STREAMS, portraitGrid = TWO_COLUMNS, landscapeGrid = TWO_COLUMNS)
    launchApp()
    home { assertShowsStreams(STREAMS) }
  }

  @Test
  fun pausingDisconnectsAndResumingReconnects() {
    assertEquals(1, vpn.connectCalls.get())
    assertEquals(1, vpnLifecycle.startCalls.get())

    moveActivityTo(Lifecycle.State.STARTED)

    assertEquals(1, vpn.disconnectCalls.get())
    assertEquals(1, vpnLifecycle.stopCalls.get())
    // No screen assertion while paused: the Compose test rule only finds resumed activities.

    moveActivityTo(Lifecycle.State.RESUMED)

    assertEquals(2, vpn.connectCalls.get())
    assertEquals(2, vpnLifecycle.startCalls.get())
    home { assertShowsStreams(STREAMS) }
  }

  @Test
  fun pausedHomeReleasesStreams() {
    assertEquals(STREAMS.toSet(), streams.connected)

    moveActivityTo(Lifecycle.State.STARTED)

    // Without the tunnel Home shows its spinner, so the players leave the composition.
    assertEquals(emptySet<String>(), streams.connected)

    moveActivityTo(Lifecycle.State.RESUMED)

    assertEquals(STREAMS.toSet(), streams.connected)
  }
}
