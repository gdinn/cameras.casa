package com.gdisys.cameras.feature.streamurls.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.components.LoadingScreen
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.feature.streamurls.GridInput
import com.gdisys.cameras.feature.streamurls.StreamURLsDialog
import com.gdisys.cameras.feature.streamurls.StreamURLsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamURLsScreen(
  uiState: StreamURLsUiState,
  onBack: () -> Unit,
  onShowAddUrlForm: () -> Unit,
  onPortChanged: (String) -> Unit,
  onStreamNameChanged: (String) -> Unit,
  onAddUrlConfirmed: () -> Unit,
  onCancelAddUrlForm: () -> Unit,
  onRemoveUrl: (String) -> Unit,
  onLoadDefaultsRequested: () -> Unit,
  onLoadDefaultsConfirmed: () -> Unit,
  onSaveUrlsRequested: () -> Unit,
  onSaveUrlsConfirmed: () -> Unit,
  onGridColumnsChanged: (StreamOrientation, String) -> Unit,
  onGridRowsChanged: (StreamOrientation, String) -> Unit,
  onGridDynamicRowsChanged: (StreamOrientation, Boolean) -> Unit,
  onSaveGridRequested: () -> Unit,
  onSaveGridConfirmed: () -> Unit,
  onDiscardChangesConfirmed: () -> Unit,
  onDialogDismissed: () -> Unit
) {
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.stream_urls_screen_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.stream_urls_screen_navigate_back)
            )
          }
        }
      )
    }
  ) { innerPadding ->
    if (uiState.isLoading) {
      LoadingScreen(stringResource(R.string.stream_urls_screen_loading))
      return@Scaffold
    }

    // Um único container de scroll para as duas seções: a lista de URLs é aberta e a seção do
    // grid tem altura fixa, então aninhar scrolls verticais não é uma opção.
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      item {
        Text(
          text = stringResource(R.string.stream_urls_screen_disclaimer),
          style = MaterialTheme.typography.bodySmall
        )
      }

      streamUrlsSection(
        uiState = uiState,
        onShowAddUrlForm = onShowAddUrlForm,
        onPortChanged = onPortChanged,
        onStreamNameChanged = onStreamNameChanged,
        onAddUrlConfirmed = onAddUrlConfirmed,
        onCancelAddUrlForm = onCancelAddUrlForm,
        onRemoveUrl = onRemoveUrl,
        onLoadDefaultsRequested = onLoadDefaultsRequested,
        onSaveUrlsRequested = onSaveUrlsRequested
      )

      gridPreferencesSection(
        uiState = uiState,
        onColumnsChanged = onGridColumnsChanged,
        onRowsChanged = onGridRowsChanged,
        onDynamicRowsChanged = onGridDynamicRowsChanged,
        onSaveGridRequested = onSaveGridRequested
      )
    }
  }

  StreamURLsDialogs(
    dialog = uiState.dialog,
    onLoadDefaultsConfirmed = onLoadDefaultsConfirmed,
    onSaveUrlsConfirmed = onSaveUrlsConfirmed,
    onSaveGridConfirmed = onSaveGridConfirmed,
    onDiscardChangesConfirmed = onDiscardChangesConfirmed,
    onDismiss = onDialogDismissed
  )
}

/** Seção A da tela: conjunto canônico de URLs. */
private fun LazyListScope.streamUrlsSection(
  uiState: StreamURLsUiState,
  onShowAddUrlForm: () -> Unit,
  onPortChanged: (String) -> Unit,
  onStreamNameChanged: (String) -> Unit,
  onAddUrlConfirmed: () -> Unit,
  onCancelAddUrlForm: () -> Unit,
  onRemoveUrl: (String) -> Unit,
  onLoadDefaultsRequested: () -> Unit,
  onSaveUrlsRequested: () -> Unit
) {
  item {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        modifier = Modifier.weight(1f),
        text = stringResource(R.string.stream_urls_screen_urls_section_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
      IconButton(
        onClick = onShowAddUrlForm,
        enabled = uiState.isAddUrlButtonEnabled
      ) {
        Icon(
          imageVector = Icons.Filled.Add,
          contentDescription = stringResource(R.string.stream_urls_screen_add_url)
        )
      }
    }
  }

  if (uiState.streamUrls.isEmpty()) {
    item {
      Text(
        text = stringResource(R.string.stream_urls_screen_no_urls),
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }

  items(uiState.streamUrls, key = { it }) { url ->
    StreamUrlRow(
      url = url,
      onRemove = { onRemoveUrl(url) }
    )
  }

  if (uiState.isAddFormVisible) {
    item {
      AddStreamUrlForm(
        port = uiState.portInput,
        streamName = uiState.streamNameInput,
        onPortChanged = onPortChanged,
        onStreamNameChanged = onStreamNameChanged,
        onConfirm = onAddUrlConfirmed,
        onCancel = onCancelAddUrlForm
      )
    }
  }

  item {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedButton(onClick = onLoadDefaultsRequested) {
        Text(stringResource(R.string.stream_urls_screen_load_defaults))
      }
      Button(onClick = onSaveUrlsRequested) {
        Text(stringResource(R.string.stream_urls_screen_save_urls))
      }
    }
  }
}

@Composable
private fun StreamURLsDialogs(
  dialog: StreamURLsDialog?,
  onLoadDefaultsConfirmed: () -> Unit,
  onSaveUrlsConfirmed: () -> Unit,
  onSaveGridConfirmed: () -> Unit,
  onDiscardChangesConfirmed: () -> Unit,
  onDismiss: () -> Unit
) {
  when (dialog) {
    null -> Unit

    StreamURLsDialog.ConfirmLoadDefaults -> ConfirmationDialog(
      title = stringResource(R.string.stream_urls_dialog_load_defaults_title),
      message = stringResource(R.string.stream_urls_dialog_load_defaults_message),
      confirmLabel = stringResource(R.string.stream_urls_dialog_confirm),
      onConfirm = onLoadDefaultsConfirmed,
      onDismiss = onDismiss
    )

    StreamURLsDialog.ConfirmSaveUrls -> ConfirmationDialog(
      title = stringResource(R.string.stream_urls_dialog_save_title),
      message = stringResource(R.string.stream_urls_dialog_save_message),
      confirmLabel = stringResource(R.string.stream_urls_dialog_confirm),
      onConfirm = onSaveUrlsConfirmed,
      onDismiss = onDismiss
    )

    StreamURLsDialog.ConfirmSaveGrid -> ConfirmationDialog(
      title = stringResource(R.string.stream_urls_dialog_save_grid_title),
      message = stringResource(R.string.stream_urls_dialog_save_grid_message),
      confirmLabel = stringResource(R.string.stream_urls_dialog_confirm),
      onConfirm = onSaveGridConfirmed,
      onDismiss = onDismiss
    )

    StreamURLsDialog.ConfirmDiscard -> ConfirmationDialog(
      title = stringResource(R.string.stream_urls_dialog_discard_title),
      message = stringResource(R.string.stream_urls_dialog_discard_message),
      confirmLabel = stringResource(R.string.stream_urls_dialog_discard_confirm),
      onConfirm = onDiscardChangesConfirmed,
      onDismiss = onDismiss
    )
  }
}

@Composable
private fun ConfirmationDialog(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.stream_urls_dialog_dismiss))
      }
    }
  )
}

@Preview
@Composable
fun StreamURLsScreenPreview() {
  StreamURLsScreen(
    uiState = StreamURLsUiState(
      isLoading = false,
      streamUrls = listOf(
        "http://[fd00:20::cafe]:8889/cam_160",
        "http://[fd00:20::cafe]:8889/cam_161"
      ),
      portraitGridInput = GridInput(columns = "1", rows = "1", dynamicRows = true),
      landscapeGridInput = GridInput(columns = "2", rows = "1", dynamicRows = true)
    ),
    onBack = {},
    onShowAddUrlForm = {},
    onPortChanged = {},
    onStreamNameChanged = {},
    onAddUrlConfirmed = {},
    onCancelAddUrlForm = {},
    onRemoveUrl = {},
    onLoadDefaultsRequested = {},
    onLoadDefaultsConfirmed = {},
    onSaveUrlsRequested = {},
    onSaveUrlsConfirmed = {},
    onGridColumnsChanged = { _, _ -> },
    onGridRowsChanged = { _, _ -> },
    onGridDynamicRowsChanged = { _, _ -> },
    onSaveGridRequested = {},
    onSaveGridConfirmed = {},
    onDiscardChangesConfirmed = {},
    onDialogDismissed = {}
  )
}

@Preview
@Composable
fun StreamURLsScreenAddingUrlPreview() {
  StreamURLsScreen(
    uiState = StreamURLsUiState(
      isLoading = false,
      streamUrls = emptyList(),
      isAddFormVisible = true,
      portInput = "8889",
      streamNameInput = "cam_160",
      portraitGridInput = GridInput(columns = "1", rows = "1", dynamicRows = true),
      landscapeGridInput = GridInput(columns = "2", rows = "2", dynamicRows = false)
    ),
    onBack = {},
    onShowAddUrlForm = {},
    onPortChanged = {},
    onStreamNameChanged = {},
    onAddUrlConfirmed = {},
    onCancelAddUrlForm = {},
    onRemoveUrl = {},
    onLoadDefaultsRequested = {},
    onLoadDefaultsConfirmed = {},
    onSaveUrlsRequested = {},
    onSaveUrlsConfirmed = {},
    onGridColumnsChanged = { _, _ -> },
    onGridRowsChanged = { _, _ -> },
    onGridDynamicRowsChanged = { _, _ -> },
    onSaveGridRequested = {},
    onSaveGridConfirmed = {},
    onDiscardChangesConfirmed = {},
    onDialogDismissed = {}
  )
}
