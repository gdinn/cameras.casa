package com.gdisys.cameras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
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
      CamerasTheme {
        CompositionLocalProvider(LocalEglBase provides eglBase) {
          NavigationRoot(navController = rememberNavController())
        }
      }
    }
  }
}