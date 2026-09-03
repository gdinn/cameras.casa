package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

class OverscrollHistoryState(
  val nestedScrollConnection: NestedScrollConnection,
  val isHistoryButtonVisible: Boolean,
)

@Composable
fun rememberOverscrollHistory(gridState: LazyGridState): OverscrollHistoryState {
  val density = LocalDensity.current
  val configuration = LocalConfiguration.current
  val overscrollThresholdPx = remember(configuration, density) {
    with(density) { (configuration.screenHeightDp / 2).dp.toPx() }
  }
  var overscrollPx by remember { mutableFloatStateOf(0f) }
  var isHistoryButtonVisible by remember { mutableStateOf(false) }

  val nestedScrollConnection = remember(gridState, overscrollThresholdPx) {
    object : NestedScrollConnection {
      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
      ): Offset {
        if (source != NestedScrollSource.Drag) return Offset.Zero

        // Só reconhece overscroll quando o usuário já está no início da lista.
        if (gridState.canScrollBackward) {
          overscrollPx = 0f
          isHistoryButtonVisible = false
          return Offset.Zero
        }

        overscrollPx = (overscrollPx + available.y).coerceAtLeast(0f)
        if (overscrollPx >= overscrollThresholdPx) {
          isHistoryButtonVisible = true
        }
        return Offset.Zero
      }
    }
  }

  return OverscrollHistoryState(nestedScrollConnection, isHistoryButtonVisible)
}
