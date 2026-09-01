package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.gdisys.cameras.core.webrtc.domain.WhepClient
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

data class WebRtcConnection(
  val eglBase: EglBase,
  val startConnection: suspend (streamUrl: String, renderer: SurfaceViewRenderer) -> WhepClient?
)

val LocalWebRtcConnection = staticCompositionLocalOf<WebRtcConnection> {
  error("LocalWebRtcConnection not provided")
}
