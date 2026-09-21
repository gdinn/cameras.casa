package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import kotlin.math.roundToInt

/**
 * "Reconfigure", floating over the fixed-mode grid.
 *
 * There is no scrolling here to reveal it — that is `OverscrollReconfigure`'s job in dynamic mode —
 * so the button is always visible, and draggable, so it can be moved off whichever stream it
 * happens to cover.
 *
 * Its position is an offset from the bottom-right corner, clamped to the container's size, so the
 * button can never be dragged off screen.
 *
 * @param containerWidth available width, used to clamp the drag
 * @param containerHeight available height, used to clamp the drag
 */
@Composable
fun BoxScope.DraggableReconfigureButton(
  containerWidth: Dp,
  containerHeight: Dp,
  onClick: () -> Unit,
  margin: Dp = 16.dp
) {
  var offset by remember { mutableStateOf(Offset.Zero) }
  var buttonSize by remember { mutableStateOf(IntSize.Zero) }

  val currentContainerWidth by rememberUpdatedState(containerWidth)
  val currentContainerHeight by rememberUpdatedState(containerHeight)
  val currentMargin by rememberUpdatedState(margin)

  ExtendedFloatingActionButton(
    onClick = onClick,
    modifier = Modifier
      .align(Alignment.BottomEnd)
      .padding(currentMargin)
      .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
      .onSizeChanged { buttonSize = it }
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          val marginPx = currentMargin.toPx()
          // The anchor is the bottom-right corner, so the offset can only be negative, and only
          // as far as the opposite corner.
          val minX = -(currentContainerWidth.toPx() - buttonSize.width - 2 * marginPx)
          val minY = -(currentContainerHeight.toPx() - buttonSize.height - 2 * marginPx)
          offset = Offset(
            x = (offset.x + dragAmount.x).coerceIn(minX.coerceAtMost(0f), 0f),
            y = (offset.y + dragAmount.y).coerceIn(minY.coerceAtMost(0f), 0f)
          )
        }
      }
  ) {
    Text(text = stringResource(R.string.home_screen_reconfigure))
  }
}
