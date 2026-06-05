package com.gdisys.cameras.app.navigation

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.feature.InitScreen
import com.gdisys.cameras.feature.ConfigScreen

@Composable
fun NavigationRoot(
  activity: Activity,
  navController: NavHostController,
  dataStoreManager: DataStoreManager,
) {
  NavHost(
    navController = navController,
    startDestination = NavigationRoute.Loading
  ) {
    composable<NavigationRoute.Loading> {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
      ) {
        InitScreen(
          dataStoreManager,
          onNavigateToConfig = {
            navController.navigate(NavigationRoute.Config)
          },
          onNavigateToDashboard = {
            navController.navigate(NavigationRoute.Home)
          }
        )
      }
    }

    composable<NavigationRoute.Config> {
      ConfigScreen(
        activity,
        dataStoreManager
      )
    }

    composable<NavigationRoute.Home> {

    }
  }

}
