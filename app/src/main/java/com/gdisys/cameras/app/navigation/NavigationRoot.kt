package com.gdisys.cameras.app.navigation

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.feature.InitScreen
import com.gdisys.cameras.feature.ConfigScreen
import com.gdisys.cameras.feature.cameras.CamerasLoadingScreen
import com.gdisys.cameras.feature.cameras.DashboardScreen
import com.gdisys.cameras.feature.cameras.HomeViewModel
import com.wireguard.android.backend.Tunnel

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
        // Avaliar uso de viewModel p/ injetar o dataStoreManager
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
      // Avaliar uso de viewModel p/ injetar o dataStoreManager
      ConfigScreen(
        activity,
        dataStoreManager
      )
    }

    composable<NavigationRoute.Home> {
      val homeViewModel: HomeViewModel = viewModel()
      val vpnState by homeViewModel.vpnState.collectAsState()
      val isConnecting by homeViewModel.isConnecting.collectAsState()
      val vpnReady = vpnState == Tunnel.State.UP && !isConnecting

      if (vpnReady) {
        DashboardScreen()
      } else {
        CamerasLoadingScreen()
      }
    }
  }

}
