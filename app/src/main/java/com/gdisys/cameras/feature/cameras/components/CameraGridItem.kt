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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R

/**
 * One cell of the grid.
 *
 * Dragging starts from a dedicated *handle* rather than from the whole card, so it does not compete
 * with the tap that puts a stream in focus. [dragHandleModifier] comes from [ReorderableStreamGrid]
 * (dynamic mode) or from [PagedStreamGrid] (fixed mode).
 *
 * Dragging is out of reach for TalkBack, so the card also offers the same reorder as accessibility
 * actions — [onMoveEarlier] / [onMoveLater], `null` at the ends of the list — and, in dynamic mode,
 * [onReconfigure], since the overscroll that reveals the button there is a drag too. Tests use the
 * same actions to reorder without coordinates.
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
  onMoveEarlier: (() -> Unit)?,
  onMoveLater: (() -> Unit)?,
  modifier: Modifier = Modifier,
  aspectRatio: Float? = null,
  onReconfigure: (() -> Unit)? = null
) {
  val moveEarlierLabel = stringResource(R.string.home_screen_move_stream_earlier)
  val moveLaterLabel = stringResource(R.string.home_screen_move_stream_later)
  val reconfigureLabel = stringResource(R.string.home_screen_reconfigure)
  val actions = listOfNotNull(
    onMoveEarlier?.let { action -> CustomAccessibilityAction(moveEarlierLabel) { action(); true } },
    onMoveLater?.let { action -> CustomAccessibilityAction(moveLaterLabel) { action(); true } },
    onReconfigure?.let { action -> CustomAccessibilityAction(reconfigureLabel) { action(); true } }
  )

  VideoStreamCard(
    videoContent = videoContent,
    modifier = modifier
      .testTag(HomeTestTags.streamCard(url))
      .semantics { customActions = actions },
    aspectRatio = aspectRatio,
    onClick = { onFocusedStreamChange(url) }
  ) {
    Icon(
      imageVector = Icons.Default.DragHandle,
      contentDescription = stringResource(R.string.home_screen_drag_handle),
      tint = Color.White,
      modifier = dragHandleModifier
        .testTag(HomeTestTags.dragHandle(url))
        .align(Alignment.TopEnd)
        .padding(8.dp)
        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        .size(32.dp)
        .padding(4.dp)
    )
  }
}
