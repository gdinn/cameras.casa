package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.gdisys.cameras.feature.cameras.logic.DEFAULT_STREAM_ASPECT_RATIO

/**
 * Proporção real de cada stream, alimentada pelos `RendererEvents` do `SurfaceViewRenderer`.
 *
 * O dimensionamento das células depende da resolução do vídeo, que só é conhecida quando o primeiro
 * frame chega; até lá vale [DEFAULT_STREAM_ASPECT_RATIO] (16:9). Como é um `mutableStateMap`, a
 * grade se re-mede sozinha assim que a resolução verdadeira aparece.
 */
@Stable
class StreamResolutionRegistry {
  private val aspectRatios = mutableStateMapOf<String, Float>()

  /** Proporção conhecida de [streamUrl], ou 16:9 enquanto não houver primeiro frame. */
  fun aspectRatioOf(streamUrl: String): Float =
    aspectRatios[streamUrl] ?: DEFAULT_STREAM_ASPECT_RATIO

  /**
   * Registra a resolução reportada pelo renderer. Com [rotation] de 90°/270° o vídeo é exibido
   * deitado, então as dimensões trocam de lugar.
   */
  fun onFrameResolutionChanged(streamUrl: String, width: Int, height: Int, rotation: Int) {
    if (width <= 0 || height <= 0) return
    val rotated = rotation % 180 != 0
    val displayWidth = if (rotated) height else width
    val displayHeight = if (rotated) width else height
    aspectRatios[streamUrl] = displayWidth.toFloat() / displayHeight.toFloat()
  }

  /** Esquece o que sabia de [streamUrl] — a próxima conexão volta a começar em 16:9. */
  fun forget(streamUrl: String) {
    aspectRatios.remove(streamUrl)
  }
}

val LocalStreamResolutions = staticCompositionLocalOf<StreamResolutionRegistry> {
  error("LocalStreamResolutions not provided")
}
