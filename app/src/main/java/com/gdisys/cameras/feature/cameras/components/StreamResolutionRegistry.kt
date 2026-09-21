package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.gdisys.cameras.feature.cameras.logic.DEFAULT_STREAM_ASPECT_RATIO

/**
 * Each stream's real aspect ratio, fed by the `SurfaceViewRenderer`'s `RendererEvents`.
 *
 * Cell sizing depends on the video resolution, which is only known once the first frame arrives;
 * until then [DEFAULT_STREAM_ASPECT_RATIO] (16:9) applies. Being a `mutableStateMap`, the grid
 * re-measures itself as soon as the real resolution turns up.
 */
@Stable
class StreamResolutionRegistry {
  private val aspectRatios = mutableStateMapOf<String, Float>()

  /** Known ratio of [streamUrl], or 16:9 while no first frame has arrived. */
  fun aspectRatioOf(streamUrl: String): Float =
    aspectRatios[streamUrl] ?: DEFAULT_STREAM_ASPECT_RATIO

  /**
   * Records the resolution the renderer reported. At a [rotation] of 90 or 270 degrees the video
   * is displayed on its side, so the dimensions swap.
   */
  fun onFrameResolutionChanged(streamUrl: String, width: Int, height: Int, rotation: Int) {
    if (width <= 0 || height <= 0) return
    val rotated = rotation % 180 != 0
    val displayWidth = if (rotated) height else width
    val displayHeight = if (rotated) width else height
    aspectRatios[streamUrl] = displayWidth.toFloat() / displayHeight.toFloat()
  }

  /** Forgets what it knew about [streamUrl] — the next connection starts back at 16:9. */
  fun forget(streamUrl: String) {
    aspectRatios.remove(streamUrl)
  }
}

val LocalStreamResolutions = staticCompositionLocalOf<StreamResolutionRegistry> {
  error("LocalStreamResolutions not provided")
}
