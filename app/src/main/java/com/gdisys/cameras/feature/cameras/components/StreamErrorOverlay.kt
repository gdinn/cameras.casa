package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R

/**
 * Drawn over a stream whose connection failed, in the grid and in focus alike. The scrim and the
 * white content stay fixed across themes, like every other overlay on top of video.
 */
@Composable
fun StreamErrorOverlay(
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.6f))
      .padding(8.dp)
      .testTag(HomeTestTags.STREAM_ERROR_OVERLAY),
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = Icons.Default.VideocamOff,
      contentDescription = null,
      tint = Color.White
    )
    Text(
      text = stringResource(R.string.home_screen_stream_unavailable),
      color = Color.White,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )
    Button(
      modifier = Modifier.testTag(HomeTestTags.STREAM_ERROR_RETRY_BUTTON),
      onClick = onRetry
    ) {
      Text(text = stringResource(R.string.home_screen_retry_stream))
    }
  }
}

@Preview
@Composable
fun StreamErrorOverlayPreview() {
  StreamErrorOverlay(onRetry = {})
}
