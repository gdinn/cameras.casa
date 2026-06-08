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
import com.gdisys.cameras.feature.cameras.components.CamerasLoadingScreen
import com.gdisys.cameras.feature.cameras.HomeViewModel
import com.gdisys.cameras.feature.cameras.components.HomeScreen
import com.gdisys.cameras.feature.config.ConfigRoute
import com.gdisys.cameras.feature.config.ConfigViewModel
import com.gdisys.cameras.feature.init.InitRoute
import com.wireguard.android.backend.Tunnel

@Composable
fun NavigationRoot(
  navController: NavHostController
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
        val configViewModel: ConfigViewModel = hiltViewModel()

        InitRoute(
          configViewModel,
          onNavigateToConfig = {
            navController.navigate(NavigationRoute.Config)
          },
          onNavigateToHome = {
            navController.navigate(NavigationRoute.Home) {
              popUpTo<NavigationRoute.Loading> {
                inclusive = true
              }
              launchSingleTop = true
            }
          }
        )
      }
    }

    composable<NavigationRoute.Config> {
      val configViewModel: ConfigViewModel = hiltViewModel()
      ConfigRoute(configViewModel)
    }

    composable<NavigationRoute.Home> {
      val homeViewModel: HomeViewModel = hiltViewModel()
      val lifecycleOwner = LocalLifecycleOwner.current

      // Observador de ciclo de vida exclusivo para a Home
      DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_RESUME -> {
              homeViewModel.connectVpn()
            }
            Lifecycle.Event.ON_PAUSE -> {
              homeViewModel.disconnectVpn()
            }
            else -> { /* Ignorar */
            }
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
          lifecycleOwner.lifecycle.removeObserver(observer)
        }
      }

      val vpnState by homeViewModel.vpnState.collectAsState()
      val isConnecting by homeViewModel.isConnecting.collectAsState()
      val vpnReady = vpnState == Tunnel.State.UP && !isConnecting
      if (vpnReady) {
        HomeScreen()
      } else {
        CamerasLoadingScreen()
      }
    }
  }
}
