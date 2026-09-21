package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gdisys.cameras.feature.cameras.logic.DEFAULT_STREAM_ASPECT_RATIO

@Composable
fun FocusedStreamView(
  videoContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  aspectRatio: Float = DEFAULT_STREAM_ASPECT_RATIO
) {
  VideoStreamCard(
    videoContent = videoContent,
    modifier = modifier,
    aspectRatio = aspectRatio
  )
}
