package com.gdisys.cameras.feature.cameras.components

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.gdisys.cameras.core.webrtc.domain.WhepClient
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

@Composable
fun WebRtcVideoPlayer(
  streamUrl: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val connection = LocalWebRtcConnection.current
  val scope = rememberCoroutineScope()
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
  var whepClient : WhepClient? = null
  DisposableEffect(streamUrl) {
    val job = scope.launch {
      whepClient = connection.startConnection(streamUrl, renderer)
    }
    onDispose {
      job.cancel()
      whepClient?.close()
    }

  }
  AndroidView(factory = { renderer }, modifier = modifier)
}
