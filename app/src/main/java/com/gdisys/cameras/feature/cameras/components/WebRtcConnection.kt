package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import org.webrtc.EglBase
import org.webrtc.VideoSink

data class WebRtcConnection(
  val eglBase: EglBase,
  val connect: (streamUrl: String, videoSink: VideoSink) -> Unit,
  val disconnect: (streamUrl: String) -> Unit
)

val LocalWebRtcConnection = staticCompositionLocalOf<WebRtcConnection> {
  error("LocalWebRtcConnection not provided")
}

/**
 * The streams whose last connection attempt failed, read by each [WebRtcVideoPlayer] to draw
 * [StreamErrorOverlay]. A local rather than a parameter because the players are movable content
 * created once per URL: a value captured at creation would never change. Not static, so a failure
 * recomposes only the players that read it.
 */
val LocalFailedStreams = compositionLocalOf<Set<String>> { emptySet() }
