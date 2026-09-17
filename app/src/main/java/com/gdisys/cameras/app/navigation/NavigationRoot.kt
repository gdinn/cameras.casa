package com.gdisys.cameras.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gdisys.cameras.core.components.QrCodeRoute
import com.gdisys.cameras.feature.cameras.HomeRoute
import com.gdisys.cameras.feature.config.ConfigRoute
import com.gdisys.cameras.feature.config.QR_CODE_RESULT_KEY
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

    composable<NavigationRoute.Config> { backStackEntry ->
      val qrCodeRawJsonResult by backStackEntry.savedStateHandle
        .getStateFlow<String?>(QR_CODE_RESULT_KEY, null)
        .collectAsState()

      ConfigRoute(
        qrCodeRawJsonResult = qrCodeRawJsonResult,
        onQrCodeResultConsumed = {
          backStackEntry.savedStateHandle[QR_CODE_RESULT_KEY] = null
        },
        onNavigateToScanner = {
          navController.navigate(NavigationRoute.QrCode)
        },
        onNavigateToHome = {
          navigateToHome(navController)
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