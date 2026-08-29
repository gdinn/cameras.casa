package com.gdisys.cameras.feature.cameras

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gdisys.cameras.feature.cameras.components.CamerasLoadingScreen
import com.gdisys.cameras.feature.cameras.components.HomeScreen
import com.wireguard.android.backend.Tunnel

@Composable
fun HomeRoute(
  viewModel: HomeViewModel,
  onNavigateToConfig: () -> Unit
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  // Observador de ciclo de vida exclusivo para a Home
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          viewModel.connectVpn()
        }
        Lifecycle.Event.ON_PAUSE -> {
          viewModel.disconnectVpn()
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

  val vpnState by viewModel.vpnState.collectAsState()
  val isConnecting by viewModel.isConnecting.collectAsState()
  val vpnReady = vpnState == Tunnel.State.UP && !isConnecting
  if (vpnReady) {
    val streams by viewModel.streams.collectAsState()
    val focusedStream by viewModel.focusedStream.collectAsState()
    HomeScreen(
      streams = streams,
      focusedStream = focusedStream,
      onFocusStream = viewModel::focusStream,
      onClearFocusedStream = viewModel::clearFocusedStream,
      onMoveStreamUp = viewModel::moveStreamUp,
      onMoveStreamDown = viewModel::moveStreamDown,
      onNavigateToConfig = onNavigateToConfig
    )
  } else {
    CamerasLoadingScreen()
  }
}