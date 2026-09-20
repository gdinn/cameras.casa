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
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.core.components.LoadingScreen
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.feature.cameras.components.EmptyStreamsScreen
import com.gdisys.cameras.feature.cameras.components.HomeScreen
import org.webrtc.EglBase

@Composable
fun HomeRoute(
  viewModel: HomeViewModel = hiltViewModel(),
  eglBase: EglBase,
  onNavigateToConfig: () -> Unit
) {
  val activity = LocalActivity.current

  // Decisão P1: a orientação vem da Configuration e é empurrada para o ViewModel, que escolhe a
  // ordem e a grade correspondentes.
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
