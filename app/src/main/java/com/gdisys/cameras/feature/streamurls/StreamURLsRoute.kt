package com.gdisys.cameras.feature.streamurls

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.streamurls.components.StreamURLsScreen

@Composable
fun StreamURLsRoute(
  viewModel: StreamURLsViewModel = hiltViewModel(),
  onNavigateBack: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // O back do sistema segue o mesmo caminho do botão da TopBar: com pendências, pede descarte.
  BackHandler {
    viewModel.onBackRequested()
  }

  LaunchedEffect(viewModel) {
    viewModel.navigateBackEvent.collect {
      onNavigateBack()
    }
  }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  StreamURLsScreen(
    uiState = uiState,
    onBack = viewModel::onBackRequested,
    onShowAddUrlForm = viewModel::onShowAddUrlForm,
    onPortChanged = viewModel::onPortChanged,
    onStreamNameChanged = viewModel::onStreamNameChanged,
    onAddUrlConfirmed = viewModel::onAddUrlConfirmed,
    onCancelAddUrlForm = viewModel::onCancelAddUrlForm,
    onRemoveUrl = viewModel::onRemoveUrl,
    onLoadDefaultsRequested = viewModel::onLoadDefaultsRequested,
    onLoadDefaultsConfirmed = viewModel::onLoadDefaultsConfirmed,
    onSaveUrlsRequested = viewModel::onSaveUrlsRequested,
    onSaveUrlsConfirmed = viewModel::onSaveUrlsConfirmed,
    onGridColumnsChanged = viewModel::onGridColumnsChanged,
    onGridRowsChanged = viewModel::onGridRowsChanged,
    onGridDynamicRowsChanged = viewModel::onGridDynamicRowsChanged,
    onSaveGridRequested = viewModel::onSaveGridRequested,
    onSaveGridConfirmed = viewModel::onSaveGridConfirmed,
    onDiscardChangesConfirmed = viewModel::onDiscardChangesConfirmed,
    onDialogDismissed = viewModel::onDialogDismissed
  )
}
