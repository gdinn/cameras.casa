package com.gdisys.cameras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.gdisys.cameras.app.navigation.NavigationRoot
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.ui.theme.CamerasTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CamerasTheme {
        NavigationRoot(
          navController = rememberNavController()
        )
      }
    }
  }
}