package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun FocusedStreamView(
  streamUrl: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .aspectRatio(16f / 9f)
      .clip(RoundedCornerShape(8.dp))
  ) {
    WebRtcVideoPlayer(
      streamUrl = streamUrl,
      modifier = Modifier.fillMaxSize()
    )
  }
}
