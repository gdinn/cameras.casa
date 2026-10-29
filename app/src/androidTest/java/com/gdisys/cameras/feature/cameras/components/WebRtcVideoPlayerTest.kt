package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import com.gdisys.cameras.testing.setThemedContent
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.EglBase
import org.webrtc.VideoSink
import java.util.concurrent.CopyOnWriteArrayList

private const val STREAM_URL = "http://[fd00:20::cafe]:8889/cam_160"

/**
 * The real player and renderer over a recording [WebRtcConnection], so no WHEP connection is made.
 * The failure comes in through [LocalFailedStreams], the way `HomeScreen` provides it.
 */
@RunWith(AndroidJUnit4::class)
class WebRtcVideoPlayerTest {

  @get:Rule
  val composeRule = createComposeRule()

  // Not released: the renderer still uses it until the rule disposes the composition, after any
  // @After. The orchestrator runs each test in a new process anyway.
  private val eglBase = EglBase.create()

  private val connects = CopyOnWriteArrayList<Pair<String, VideoSink>>()
  private val disconnects = CopyOnWriteArrayList<String>()

  @Test
  fun connectedStreamHasNoErrorOverlay() {
    setPlayer(failedStreams = emptySet())

    composeRule.onNodeWithTag(HomeTestTags.STREAM_ERROR_OVERLAY).assertDoesNotExist()
    assertEquals(listOf(STREAM_URL), connects.map { it.first })
  }

  @Test
  fun failedStreamShowsTheErrorOverlay() {
    setPlayer(failedStreams = setOf(STREAM_URL))

    composeRule.onNodeWithText(string(R.string.home_screen_stream_unavailable)).assertIsDisplayed()
    composeRule.onNodeWithTag(HomeTestTags.STREAM_ERROR_RETRY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun retryReconnectsTheSameRenderer() {
    setPlayer(failedStreams = setOf(STREAM_URL))

    composeRule.onNodeWithTag(HomeTestTags.STREAM_ERROR_RETRY_BUTTON).performClick()

    composeRule.runOnIdle {
      assertEquals(listOf(STREAM_URL), disconnects.toList())
      assertEquals(listOf(STREAM_URL, STREAM_URL), connects.map { it.first })
      assertSame(connects[0].second, connects[1].second)
    }
  }

  private fun setPlayer(failedStreams: Set<String>) {
    val connection = WebRtcConnection(
      eglBase = eglBase,
      connect = { url, sink -> connects += url to sink },
      disconnect = { url -> disconnects += url }
    )
    // Built outside the composition: a new registry would make the player build a new renderer.
    val resolutions = StreamResolutionRegistry()
    composeRule.setThemedContent {
      CompositionLocalProvider(
        LocalWebRtcConnection provides connection,
        LocalStreamResolutions provides resolutions,
        LocalFailedStreams provides failedStreams
      ) {
        WebRtcVideoPlayer(streamUrl = STREAM_URL, modifier = Modifier.fillMaxSize())
      }
    }
  }
}
