package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FocusedStreamView(
  videoContent: @Composable () -> Unit,
  modifier: Modifier = Modifier
) {
  VideoStreamCard(
    videoContent = videoContent,
    modifier = modifier
  )
}
