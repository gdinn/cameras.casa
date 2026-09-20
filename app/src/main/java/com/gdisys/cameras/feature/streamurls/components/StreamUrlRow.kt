package com.gdisys.cameras.feature.streamurls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R

@Composable
fun StreamUrlRow(
  modifier: Modifier = Modifier,
  url: String,
  onRemove: () -> Unit
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(start = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.weight(1f),
      text = url,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    IconButton(onClick = onRemove) {
      Icon(
        imageVector = Icons.Filled.Remove,
        contentDescription = stringResource(R.string.stream_urls_screen_remove_url)
      )
    }
  }
}

@Preview
@Composable
fun StreamUrlRowPreview() {
  StreamUrlRow(
    url = "http://[fd00:20::cafe]:8889/cam_160",
    onRemove = {}
  )
}
