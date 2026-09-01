package com.gdisys.cameras.feature.cameras.components

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

@Composable
fun WebRtcVideoPlayer(
  streamUrl: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val connection = LocalWebRtcConnection.current
  val renderer = remember(connection.eglBase) {
    SurfaceViewRenderer(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      init(connection.eglBase.eglBaseContext, null)
      setMirror(false)
      setEnableHardwareScaler(true)
    }
  }
  DisposableEffect(streamUrl) {
    connection.connect(streamUrl, renderer)
    onDispose {
      connection.disconnect(streamUrl)
    }
  }
  AndroidView(factory = { renderer }, modifier = modifier)
}
