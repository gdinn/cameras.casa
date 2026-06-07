package com.gdisys.cameras.feature.cameras

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gdisys.cameras.feature.cameras.components.HomeScreen

@Composable
fun HomeRoute(viewModel: HomeViewModel) {
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> viewModel.connectVpn()
        Lifecycle.Event.ON_STOP -> viewModel.disconnectVpn()
        else -> {}
      }
    }

    // Observa apenas o ciclo de vida desta tela específica
    lifecycleOwner.lifecycle.addObserver(observer)

    // Limpa o observer quando o Composable sair da tela
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }
  HomeScreen()
}