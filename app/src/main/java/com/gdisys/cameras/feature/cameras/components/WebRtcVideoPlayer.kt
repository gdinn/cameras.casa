package com.gdisys.cameras.feature.cameras.components

import android.content.Context
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
  onStartWhepConnection: suspend (streamUrl: String, renderer: SurfaceViewRenderer) -> WhepClient?,
  onCreateRenderer: (context: Context) -> SurfaceViewRenderer,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val renderer = remember { onCreateRenderer(context) }
  var whepClient : WhepClient? = null
  DisposableEffect(streamUrl) {
    val job = scope.launch {
      whepClient = onStartWhepConnection(streamUrl, renderer)
    }
    onDispose {
      job.cancel()
      whepClient?.close()
    }

  }
  AndroidView(factory = { renderer }, modifier = modifier)
}
