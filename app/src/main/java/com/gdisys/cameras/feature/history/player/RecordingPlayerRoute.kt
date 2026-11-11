package com.gdisys.cameras.feature.history.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.history.player.components.RecordingPlayerScreen

@Composable
fun RecordingPlayerRoute(
  viewModel: RecordingPlayerViewModel = hiltViewModel(),
  onNavigateBack: () -> Unit
) {
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPaused() }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  RecordingPlayerScreen(
    uiState = uiState,
    video = { modifier -> viewModel.player.Video(modifier) },
    onBack = onNavigateBack,
    onPrevious = viewModel::onPrevious,
    onNext = viewModel::onNext,
    onRetry = viewModel::onRetry
  )
}
