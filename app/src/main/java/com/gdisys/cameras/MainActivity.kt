package com.gdisys.cameras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.gdisys.cameras.app.navigation.NavigationRoot
import com.gdisys.cameras.core.webrtc.LocalEglBase
import com.gdisys.cameras.ui.theme.CamerasTheme
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.EglBase
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject
  lateinit var eglBase: EglBase

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CamerasAppContent(eglBase = eglBase, navController = rememberNavController())
    }
  }
}

/**
 * The app's composition root: theme, the shared [EglBase] and the navigation graph.
 *
 * Kept apart from [MainActivity] so instrumented tests render exactly this tree inside
 * `HiltTestActivity` (debug source set) with a [NavHostController] they own, which lets them read
 * the back stack and hand a QR result back through the `savedStateHandle`.
 */
@Composable
internal fun CamerasAppContent(eglBase: EglBase, navController: NavHostController) {
  CamerasTheme {
    CompositionLocalProvider(LocalEglBase provides eglBase) {
      NavigationRoot(navController = navController)
    }
  }
}
