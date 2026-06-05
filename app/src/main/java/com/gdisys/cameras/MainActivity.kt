package com.gdisys.cameras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.gdisys.cameras.app.navigation.NavigationRoot
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.ui.theme.CamerasTheme
import javax.inject.Inject

class MainActivity : ComponentActivity() {
  @Inject
  lateinit var dataStoreManager: DataStoreManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CamerasTheme {
        NavigationRoot(
          this,
          navController = rememberNavController(),
          dataStoreManager
        )
      }
    }
  }
}