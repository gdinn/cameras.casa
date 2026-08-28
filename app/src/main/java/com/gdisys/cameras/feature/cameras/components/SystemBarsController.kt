package com.gdisys.cameras.feature.cameras.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun rememberSystemBarsController(focusedStream: String?) {
  val context = LocalContext.current
  LaunchedEffect(focusedStream) {
    val window = (context as? Activity)?.window
    if (window != null) {
      val controller = WindowCompat.getInsetsController(window, window.decorView)
      if (focusedStream != null) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
      }
    }
  }
}