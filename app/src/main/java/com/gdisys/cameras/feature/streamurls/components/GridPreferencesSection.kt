package com.gdisys.cameras.feature.streamurls.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.feature.streamurls.GridInput
import com.gdisys.cameras.feature.streamurls.StreamURLsUiState

/**
 * Seção B da tela: configuração da grade, um bloco por orientação.
 *
 * Entra como itens da `LazyColumn` da tela para que exista um único container de scroll — a seção
 * A já é uma lista, e aninhar dois scrolls verticais não é possível no Compose.
 */
fun LazyListScope.gridPreferencesSection(
  uiState: StreamURLsUiState,
  onColumnsChanged: (StreamOrientation, String) -> Unit,
  onRowsChanged: (StreamOrientation, String) -> Unit,
  onDynamicRowsChanged: (StreamOrientation, Boolean) -> Unit,
  onSaveGridRequested: () -> Unit
) {
  item {
    Text(
      text = stringResource(R.string.stream_urls_screen_grid_section_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
  }

  item {
    GridOrientationBlock(
      title = stringResource(R.string.stream_urls_screen_grid_portrait),
      gridInput = uiState.portraitGridInput,
      onColumnsChanged = { onColumnsChanged(StreamOrientation.PORTRAIT, it) },
      onRowsChanged = { onRowsChanged(StreamOrientation.PORTRAIT, it) },
      onDynamicRowsChanged = { onDynamicRowsChanged(StreamOrientation.PORTRAIT, it) }
    )
  }

  item {
    GridOrientationBlock(
      title = stringResource(R.string.stream_urls_screen_grid_landscape),
      gridInput = uiState.landscapeGridInput,
      onColumnsChanged = { onColumnsChanged(StreamOrientation.LANDSCAPE, it) },
      onRowsChanged = { onRowsChanged(StreamOrientation.LANDSCAPE, it) },
      onDynamicRowsChanged = { onDynamicRowsChanged(StreamOrientation.LANDSCAPE, it) }
    )
  }

  item {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      Button(
        onClick = onSaveGridRequested,
        enabled = uiState.isSaveGridButtonEnabled
      ) {
        Text(stringResource(R.string.stream_urls_screen_save_grid))
      }
    }
  }
}

@Composable
private fun GridOrientationBlock(
  modifier: Modifier = Modifier,
  title: String,
  gridInput: GridInput,
  onColumnsChanged: (String) -> Unit,
  onRowsChanged: (String) -> Unit,
  onDynamicRowsChanged: (Boolean) -> Unit
) {
  OutlinedCard(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(text = title, style = MaterialTheme.typography.titleSmall)

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          modifier = Modifier.weight(1f),
          value = gridInput.columns,
          onValueChange = onColumnsChanged,
          singleLine = true,
          label = { Text(stringResource(R.string.stream_urls_screen_grid_columns_label)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
          modifier = Modifier.weight(1f),
          value = gridInput.rows,
          onValueChange = onRowsChanged,
          // No modo dinâmico as linhas são ignoradas: o campo fica desabilitado, e não apagado,
          // para o usuário não perder o valor ao alternar o modo.
          enabled = !gridInput.dynamicRows,
          singleLine = true,
          label = { Text(stringResource(R.string.stream_urls_screen_grid_rows_label)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
          checked = gridInput.dynamicRows,
          onCheckedChange = onDynamicRowsChanged
        )
        Text(
          text = stringResource(R.string.stream_urls_screen_grid_dynamic_rows),
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }
  }
}

@Preview
@Composable
fun GridOrientationBlockPreview() {
  GridOrientationBlock(
    title = "Portrait",
    gridInput = GridInput(columns = "1", rows = "1", dynamicRows = true),
    onColumnsChanged = {},
    onRowsChanged = {},
    onDynamicRowsChanged = {}
  )
}

@Preview
@Composable
fun GridOrientationBlockFixedRowsPreview() {
  GridOrientationBlock(
    title = "Landscape",
    gridInput = GridInput(columns = "2", rows = "2", dynamicRows = false),
    onColumnsChanged = {},
    onRowsChanged = {},
    onDynamicRowsChanged = {}
  )
}
