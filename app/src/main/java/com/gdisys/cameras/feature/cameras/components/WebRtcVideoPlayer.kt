package com.gdisys.cameras.feature.cameras.components

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun WebRtcVideoPlayer(
  streamUrl: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val connection = LocalWebRtcConnection.current
  val resolutions = LocalStreamResolutions.current

  // init's second parameter is the RendererEvents: it is how the video's real resolution arrives,
  // and that resolution is what the cell-sizing aspect ratio is derived from.
  val rendererEvents = remember(streamUrl, resolutions) {
    object : RendererCommon.RendererEvents {
      override fun onFirstFrameRendered() = Unit

      override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        resolutions.onFrameResolutionChanged(streamUrl, videoWidth, videoHeight, rotation)
      }
    }
  }

  val renderer = remember(connection.eglBase, rendererEvents) {
    SurfaceViewRenderer(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      init(connection.eglBase.eglBaseContext, rendererEvents)
      setMirror(false)
      setEnableHardwareScaler(true)
    }
  }

  // With paging, players enter and leave the composition on every page change, so the renderer's
  // EGL resources have to be released too — closing the WHEP connection is not enough. The order
  // matters: the stream must stop writing to the sink before the renderer is released.
  DisposableEffect(streamUrl, renderer) {
    connection.connect(streamUrl, renderer)
    onDispose {
      connection.disconnect(streamUrl)
      renderer.release()
    }
  }

  AndroidView(factory = { renderer }, modifier = modifier)
}
