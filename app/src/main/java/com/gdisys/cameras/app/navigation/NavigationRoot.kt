package com.gdisys.cameras.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gdisys.cameras.feature.cameras.HomeRoute
import com.gdisys.cameras.feature.cameras.HomeViewModel
import com.gdisys.cameras.feature.config.ConfigRoute
import com.gdisys.cameras.feature.config.ConfigViewModel
import com.gdisys.cameras.feature.init.InitRoute
import com.gdisys.cameras.feature.init.InitViewModel

@Composable
fun NavigationRoot(
  navController: NavHostController
) {
  NavHost(
    navController = navController,
    startDestination = NavigationRoute.Loading
  ) {
    composable<NavigationRoute.Loading> {
      val initViewModel: InitViewModel = hiltViewModel()
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
      ) {
        InitRoute(
          initViewModel,
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
      val configViewModel: ConfigViewModel = hiltViewModel()
      ConfigRoute(
        viewModel = configViewModel,
        onNavigateToHome = {
          navigateToHome(navController)
        },
        onQrCodeScanned = {
          configViewModel.onQrCodeScanned(it)
        }
      )
    }

    composable<NavigationRoute.Home> {
      val homeViewModel: HomeViewModel = hiltViewModel()
      HomeRoute(
        viewModel = homeViewModel,
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