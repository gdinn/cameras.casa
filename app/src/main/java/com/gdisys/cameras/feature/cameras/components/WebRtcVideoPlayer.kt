package com.gdisys.cameras.feature.cameras.components

import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.gdisys.cameras.core.webrtc.data.WhepPeerConnectionClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceViewRenderer

private const val TAG = "WebRtcVideoPlayer"

@Composable
fun WebRtcVideoPlayer(
  streamUrl: String,
  factory: PeerConnectionFactory,
  eglBase: EglBase,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val renderer = remember {
    SurfaceViewRenderer(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      init(eglBase.eglBaseContext, null)
      setMirror(false)
      setEnableHardwareScaler(true)
    }
  }

  DisposableEffect(streamUrl) {
    val client = WhepPeerConnectionClient(factory)
    val job = scope.launch {
      try {
        client.connect(streamUrl, renderer)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Falha ao conectar ao stream $streamUrl", e)
      }
    }
    onDispose {
      job.cancel()
      client.close()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      renderer.release()
    }
  }

  AndroidView(factory = { renderer }, modifier = modifier)
}
