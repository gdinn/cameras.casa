package com.gdisys.cameras.feature.cameras.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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

  // Keyed like the renderer below, through rendererEvents: every new renderer starts with no frame.
  val firstFrameRendered = remember(streamUrl, resolutions, connection.eglBase) {
    mutableStateOf(false)
  }

  // init's second parameter is the RendererEvents: it is how the video's real resolution arrives,
  // and that resolution is what the cell-sizing aspect ratio is derived from.
  val rendererEvents = remember(firstFrameRendered) {
    val mainExecutor = ContextCompat.getMainExecutor(context)
    object : RendererCommon.RendererEvents {
      // Called on the renderer's own thread; the state belongs to the UI thread.
      override fun onFirstFrameRendered() = mainExecutor.execute { firstFrameRendered.value = true }

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

  val playbackState = when {
    streamUrl in LocalFailedStreams.current -> StreamPlaybackState.FAILED
    firstFrameRendered.value -> StreamPlaybackState.PLAYING
    else -> StreamPlaybackState.CONNECTING
  }

  Box(
    modifier = modifier
      .testTag(HomeTestTags.streamPlayer(streamUrl))
      .semantics { streamPlaybackState = playbackState }
  ) {
    AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())

    when (playbackState) {
      StreamPlaybackState.CONNECTING -> CircularProgressIndicator(
        color = Color.White,
        modifier = Modifier
          .align(Alignment.Center)
          .testTag(HomeTestTags.STREAM_CONNECTING_INDICATOR)
      )

      StreamPlaybackState.PLAYING -> Unit

      // Same path as leaving and coming back: drop whatever is left of the failed attempt, then
      // connect the same renderer again.
      StreamPlaybackState.FAILED -> StreamErrorOverlay(
        onRetry = {
          connection.disconnect(streamUrl)
          connection.connect(streamUrl, renderer)
        }
      )
    }
  }
}
