package com.gdisys.cameras.feature.cameras

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.R
import com.gdisys.cameras.core.components.LoadingScreen
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.core.webrtc.LocalEglBase
import com.gdisys.cameras.feature.cameras.components.EmptyStreamsScreen
import com.gdisys.cameras.feature.cameras.components.HomeScreen

@Composable
fun HomeRoute(
  viewModel: HomeViewModel = hiltViewModel(),
  onNavigateToConfig: () -> Unit
) {
  val activity = LocalActivity.current

  // HomeScreen keeps EglBase as an explicit parameter so it stays previewable; the route is the
  // layer that reads it off the composition.
  val eglBase = LocalEglBase.current

  // Only the UI can see the real device configuration, so orientation is read here and pushed
  // down to the ViewModel, which picks the matching order and grid.
  val configuration = LocalConfiguration.current
  val orientation = remember(configuration.orientation) {
    if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      StreamOrientation.LANDSCAPE
    } else {
      StreamOrientation.PORTRAIT
    }
  }
  LaunchedEffect(orientation) { viewModel.onOrientationChanged(orientation) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.connectVpn() }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.disconnectVpn() }

  BackHandler {
    viewModel.onBackPressed()
  }

  LaunchedEffect(viewModel) {
    viewModel.navigateUiEvent.collect { event ->
      when (event) {
        HomeNavigateUiEvent.ToConfig -> onNavigateToConfig()
        HomeNavigateUiEvent.ExitApp -> activity?.finish()
      }
    }
  }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  when (val state = uiState) {
    HomeUiState.Loading -> LoadingScreen(stringResource(R.string.home_screen_establishing_secure_connection))
    HomeUiState.Empty -> EmptyStreamsScreen(onNavigateToConfig = onNavigateToConfig)
    is HomeUiState.Ready -> {
      HomeScreen(
        streams = state.streams,
        focusedStream = state.focusedStream,
        grid = state.grid,
        orientation = state.orientation,
        eglBase = eglBase,
        onConnectStream = viewModel::connectStream,
        onDisconnectStream = viewModel::disconnectStream,
        onFocusStream = viewModel::focusStream,
        onClearFocusedStream = viewModel::clearFocusedStream,
        onStreamsReordered = viewModel::onStreamsReordered,
        onNavigateToConfig = onNavigateToConfig
      )
    }
  }
}
