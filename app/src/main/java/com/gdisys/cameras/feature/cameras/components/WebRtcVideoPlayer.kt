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

  // O segundo parâmetro do init é o RendererEvents: é por ele que a resolução real do vídeo chega,
  // e é dela que sai o aspect ratio usado para dimensionar as células.
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

  // Com paginação os players entram e saem da composição a cada troca de página, então os recursos
  // de EGL do renderer também precisam ser devolvidos — não basta encerrar a conexão WHEP. A ordem
  // importa: primeiro o stream deixa de escrever no sink, depois o renderer é liberado.
  DisposableEffect(streamUrl, renderer) {
    connection.connect(streamUrl, renderer)
    onDispose {
      connection.disconnect(streamUrl)
      renderer.release()
    }
  }

  AndroidView(factory = { renderer }, modifier = modifier)
}
