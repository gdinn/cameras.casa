package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FocusedStreamView(
  streamUrl: String,
  modifier: Modifier = Modifier
) {
  VideoStreamCard(
    streamUrl = streamUrl,
    modifier = modifier
  )
}
