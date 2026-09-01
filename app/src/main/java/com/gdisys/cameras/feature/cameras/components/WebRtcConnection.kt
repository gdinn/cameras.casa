package com.gdisys.cameras.feature.cameras.components

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
