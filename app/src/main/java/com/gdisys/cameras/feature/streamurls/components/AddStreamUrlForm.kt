package com.gdisys.cameras.feature.streamurls.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.feature.streamurls.domain.STREAM_URL_HOST_PREFIX

/**
 * Formulário de uma nova URL. O host é fixo, então só porta e nome do stream são editáveis; a
 * validação acontece no ViewModel, ao confirmar, e o erro volta como toast.
 */
@Composable
fun AddStreamUrlForm(
  modifier: Modifier = Modifier,
  port: String,
  streamName: String,
  onPortChanged: (String) -> Unit,
  onStreamNameChanged: (String) -> Unit,
  onConfirm: () -> Unit,
  onCancel: () -> Unit
) {
  OutlinedCard(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Prefixo fixo, exibido a partir da mesma constante usada para montar a URL.
      Text(
        text = STREAM_URL_HOST_PREFIX,
        style = MaterialTheme.typography.bodyMedium
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        OutlinedTextField(
          modifier = Modifier.weight(0.45f),
          value = port,
          onValueChange = onPortChanged,
          singleLine = true,
          label = { Text(stringResource(R.string.stream_urls_screen_port_label)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Text(text = "/")

        OutlinedTextField(
          modifier = Modifier.weight(0.55f),
          value = streamName,
          onValueChange = onStreamNameChanged,
          singleLine = true,
          label = { Text(stringResource(R.string.stream_urls_screen_stream_name_label)) }
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(onClick = onCancel) {
          Text(stringResource(R.string.stream_urls_screen_cancel_add_url))
        }
        Button(onClick = onConfirm) {
          Text(stringResource(R.string.stream_urls_screen_confirm_add_url))
        }
      }
    }
  }
}

@Preview
@Composable
fun AddStreamUrlFormPreview() {
  AddStreamUrlForm(
    port = "8889",
    streamName = "cam_164",
    onPortChanged = {},
    onStreamNameChanged = {},
    onConfirm = {},
    onCancel = {}
  )
}
