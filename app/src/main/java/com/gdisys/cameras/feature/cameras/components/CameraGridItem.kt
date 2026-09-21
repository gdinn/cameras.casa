package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R

/**
 * One cell of the grid.
 *
 * Dragging starts from a dedicated *handle* rather than from the whole card, so it does not compete
 * with the tap that puts a stream in focus. [dragHandleModifier] comes from [ReorderableStreamGrid]
 * (dynamic mode) or from [PagedStreamGrid] (fixed mode).
 *
 * @param aspectRatio the video's real ratio; `null` in fixed mode, where [modifier] already arrives
 *   with the size [FixedGrid] measured.
 */
@Composable
fun CameraGridItem(
  url: String,
  dragHandleModifier: Modifier,
  videoContent: @Composable () -> Unit,
  onFocusedStreamChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  aspectRatio: Float? = null
) {
  VideoStreamCard(
    videoContent = videoContent,
    modifier = modifier,
    aspectRatio = aspectRatio,
    onClick = { onFocusedStreamChange(url) }
  ) {
    Icon(
      imageVector = Icons.Default.DragHandle,
      contentDescription = stringResource(R.string.home_screen_drag_handle),
      tint = Color.White,
      modifier = dragHandleModifier
        .align(Alignment.TopEnd)
        .padding(8.dp)
        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        .size(32.dp)
        .padding(4.dp)
    )
  }
}
