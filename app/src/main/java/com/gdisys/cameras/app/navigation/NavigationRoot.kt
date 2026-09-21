package com.gdisys.cameras.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gdisys.cameras.core.components.QrCodeRoute
import com.gdisys.cameras.feature.cameras.HomeRoute
import com.gdisys.cameras.feature.config.ConfigRoute
import com.gdisys.cameras.feature.config.QR_CODE_RESULT_KEY
import com.gdisys.cameras.feature.init.InitRoute
import com.gdisys.cameras.feature.streamurls.StreamURLsRoute
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

    composable<NavigationRoute.Config> { backStackEntry ->
      val qrCodeRawJsonResult by backStackEntry.savedStateHandle
        .getStateFlow<String?>(QR_CODE_RESULT_KEY, null)
        .collectAsState()

      val canNavigateBackToHome = remember(backStackEntry) {
        navController.hasHomeInBackStack()
      }

      ConfigRoute(
        qrCodeRawJsonResult = qrCodeRawJsonResult,
        canNavigateBackToHome = canNavigateBackToHome,
        onQrCodeResultConsumed = {
          backStackEntry.savedStateHandle[QR_CODE_RESULT_KEY] = null
        },
        onNavigateToScanner = {
          navController.navigate(NavigationRoute.QrCode)
        },
        onNavigateToStreamURLs = {
          navController.navigate(NavigationRoute.StreamURLs)
        },
        onNavigateToHome = {
          navigateToHome(navController)
        }
      )
    }

    composable<NavigationRoute.StreamURLs> {
      StreamURLsRoute(
        onNavigateBack = {
          navController.popBackStack()
        }
      )
    }

    composable<NavigationRoute.QrCode> {
      QrCodeRoute(
        onCodeScanned = { rawJson ->
          navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(QR_CODE_RESULT_KEY, rawJson)
          navController.popBackStack()
        },
        onNavigateBack = {
          navController.popBackStack()
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
  if (navController.hasHomeInBackStack()) {
    navController.popBackStack(route = NavigationRoute.Home, inclusive = false)
  } else {
    navController.navigate(NavigationRoute.Home) {
      popUpTo(navController.graph.findStartDestination().id) {
        inclusive = true
      }
      launchSingleTop = true
    }
  }
}

private fun NavHostController.hasHomeInBackStack(): Boolean =
  currentBackStack.value.any { it.destination.hasRoute<NavigationRoute.Home>() }
