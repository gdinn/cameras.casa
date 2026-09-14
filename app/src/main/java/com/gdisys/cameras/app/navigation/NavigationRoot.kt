package com.gdisys.cameras.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gdisys.cameras.feature.cameras.HomeRoute
import com.gdisys.cameras.feature.config.ConfigRoute
import com.gdisys.cameras.feature.init.InitRoute
import org.webrtc.EglBase

@Composable
fun NavigationRoot(
  navController: NavHostController,
  eglBase: EglBase
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
        InitRoute(
          onNavigateToConfig = {
            navController.navigate(NavigationRoute.Config)
          },
          onNavigateToHome = {
            navigateToHome(navController)
          }
        )
      }
    }

    composable<NavigationRoute.Config> {
      ConfigRoute(
        onNavigateToHome = {
          navigateToHome(navController)
        }
      )
    }

    composable<NavigationRoute.Home> {
      HomeRoute(
        eglBase = eglBase,
        onNavigateToConfig = {
          navController.navigate(NavigationRoute.Config)
        }
      )
    }
  }
}

private fun navigateToHome(navController: NavHostController) {
    navController.navigate(NavigationRoute.Home) {
      popUpTo<NavigationRoute.Loading> {
        inclusive = true
      }
      launchSingleTop = true
    }
}