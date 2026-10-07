package com.gdisys.cameras.feature.cameras.components

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Immersive mode while a stream is focused: hides status and navigation bars and, since a swipe
 * brings them back transiently over the video, forces light bar icons. Leaving focus (or the
 * screen) restores the bars and the icon appearance that matches the current theme.
 */
@Composable
fun rememberSystemBarsController(focusedStream: String?) {
  val context = LocalContext.current
  val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val isFocused = focusedStream != null

  DisposableEffect(isFocused, lightBars) {
    val window = (context as? Activity)?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

    fun setLightBars(light: Boolean) {
      controller?.isAppearanceLightStatusBars = light
      controller?.isAppearanceLightNavigationBars = light
    }

    if (controller != null) {
      if (isFocused) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setLightBars(false)
      } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        setLightBars(lightBars)
      }
    }

    onDispose {
      controller?.show(WindowInsetsCompat.Type.systemBars())
      setLightBars(lightBars)
    }
  }
}
