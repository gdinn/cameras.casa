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
import com.gdisys.cameras.core.network.STREAM_URL_HOST_PREFIX

/**
 * Form for a new URL. The host is fixed, so only the port and the stream name are editable;
 * validation happens in the ViewModel on confirm, and the error comes back as a toast.
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
      // Fixed prefix, rendered from the same constant the URL is assembled with, so the label on
      // screen cannot drift from the URL actually built.
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
