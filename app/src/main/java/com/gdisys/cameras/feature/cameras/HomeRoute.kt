package com.gdisys.cameras.feature.cameras

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.feature.cameras.components.CamerasLoadingScreen
import com.gdisys.cameras.feature.cameras.components.HomeScreen

@Composable
fun HomeRoute(
  viewModel: HomeViewModel,
  onNavigateToConfig: () -> Unit
) {
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.connectVpn() }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.disconnectVpn() }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  when (val state = uiState) {
    HomeUiState.Loading -> CamerasLoadingScreen()
    is HomeUiState.Ready -> {
      HomeScreen(
        streams = state.streams,
        focusedStream = state.focusedStream,
        onStartWhepConnection = viewModel::startWhepConnection,
        onCreateRenderer = viewModel::onCreateRenderer,
        onFocusStream = viewModel::focusStream,
        onClearFocusedStream = viewModel::clearFocusedStream,
        onMoveStreamUp = viewModel::moveStreamUp,
        onMoveStreamDown = viewModel::moveStreamDown,
        onNavigateToConfig = onNavigateToConfig
      )
    }
  }
}
