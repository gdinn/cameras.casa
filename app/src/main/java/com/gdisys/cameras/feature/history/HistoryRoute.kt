package com.gdisys.cameras.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.history.components.HistoryScreen

@Composable
fun HistoryRoute(
  viewModel: HistoryViewModel = hiltViewModel(),
  onNavigateBack: () -> Unit
) {
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPaused() }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  HistoryScreen(
    uiState = uiState,
    onBack = onNavigateBack,
    onRetry = viewModel::onRetry,
    onMonthClicked = viewModel::onMonthClicked,
    onRetryMonth = viewModel::onRetryMonth,
    onDayClicked = viewModel::onDayClicked,
    onRetryDay = viewModel::onRetryDay,
    onPickerDismissed = viewModel::onPickerDismissed,
    onSlotSelected = viewModel::onSlotSelected,
    onChoiceDismissed = viewModel::onChoiceDismissed
  )
}
