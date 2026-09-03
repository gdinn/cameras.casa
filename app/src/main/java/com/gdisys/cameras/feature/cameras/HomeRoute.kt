package com.gdisys.cameras.feature.cameras

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.core.components.VpnSessionLifecycleEffect
import com.gdisys.cameras.feature.cameras.components.CamerasLoadingScreen
import com.gdisys.cameras.feature.cameras.components.HomeScreen
import org.webrtc.EglBase

@Composable
fun HomeRoute(
  viewModel: HomeViewModel,
  eglBase: EglBase,
  onNavigateToConfig: () -> Unit,
  onNavigateToHistory: () -> Unit
) {
  VpnSessionLifecycleEffect(
    onAppForegrounded = viewModel::connectVpn,
    onAppBackgrounded = viewModel::disconnectVpn
  )

  LaunchedEffect(viewModel) {
    viewModel.navigateUiEvent.collect { event ->
      when (event) {
        HomeNavigateUiEvent.ToConfig -> {
          viewModel.disconnectVpn()
          onNavigateToConfig()
        }
      }
    }
  }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  when (val state = uiState) {
    HomeUiState.Loading -> CamerasLoadingScreen()
    is HomeUiState.Ready -> {
      HomeScreen(
        streams = state.streams,
        focusedStream = state.focusedStream,
        eglBase = eglBase,
        onConnectStream = viewModel::connectStream,
        onDisconnectStream = viewModel::disconnectStream,
        onFocusStream = viewModel::focusStream,
        onClearFocusedStream = viewModel::clearFocusedStream,
        onMoveStreamUp = viewModel::moveStreamUp,
        onMoveStreamDown = viewModel::moveStreamDown,
        onNavigateToConfig = {
          viewModel.disconnectVpn()
          onNavigateToConfig()
        },
        onNavigateToHistory = onNavigateToHistory
      )
    }
  }
}
