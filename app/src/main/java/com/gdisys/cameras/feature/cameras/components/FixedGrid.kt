package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.feature.cameras.logic.fittedCellSize
import com.gdisys.cameras.feature.cameras.logic.fixedGridRowHeights

/**
 * Fixed-row grid: **no scrolling**, and it measures itself.
 *
 * Cell width is the screen divided by the column count; each row's height is that of its
 * highest-resolution video, and the whole set is scaled uniformly when it does not fit the
 * available height (see `fixedGridRowHeights`). Leftover space is expected: in **Portrait** the
 * content sits to the left and to the top; in **Landscape** it is centred on both axes.
 *
 * Cells left over when `rows x columns` exceeds the number of streams are simply not emitted —
 * filling is *row-major*, so they are always at the end.
 *
 * @param streams the streams on this page, already in the current orientation's order
 * @param onCellBounds each cell's position **in the root**, used by [PagedStreamGrid]'s drag
 */
@Composable
fun FixedGrid(
  streams: List<String>,
  columns: Int,
  orientation: StreamOrientation,
  aspectRatioOf: (String) -> Float,
  modifier: Modifier = Modifier,
  spacing: Dp = 8.dp,
  onCellBounds: (url: String, bounds: Rect) -> Unit = { _, _ -> },
  itemContent: @Composable (url: String, cellModifier: Modifier) -> Unit
) {
  val centered = orientation == StreamOrientation.LANDSCAPE

  BoxWithConstraints(modifier = modifier) {
    val safeColumns = columns.coerceAtLeast(1)
    val cellWidth = ((maxWidth - spacing * (safeColumns - 1)) / safeColumns).coerceAtLeast(0.dp)
    val rows = streams.chunked(safeColumns)
    val rowHeights = fixedGridRowHeights(
      aspectRatios = streams.map(aspectRatioOf),
      columns = safeColumns,
      cellWidth = cellWidth.value,
      availableHeight = maxHeight.value,
      verticalSpacing = spacing.value
    )

    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(
        space = spacing,
        alignment = if (centered) Alignment.CenterVertically else Alignment.Top
      )
    ) {
      rows.forEachIndexed { rowIndex, rowStreams ->
        val rowHeight = rowHeights[rowIndex]
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight.dp),
          horizontalArrangement = Arrangement.spacedBy(
            space = spacing,
            alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
          )
        ) {
          rowStreams.forEach { url ->
            val cellSize = fittedCellSize(cellWidth.value, rowHeight, aspectRatioOf(url))
            Box(
              modifier = Modifier
                .width(cellWidth)
                .fillMaxHeight()
                .onGloballyPositioned { onCellBounds(url, it.boundsInRoot()) },
              contentAlignment = if (centered) Alignment.Center else Alignment.TopStart
            ) {
              itemContent(url, Modifier.size(cellSize.width.dp, cellSize.height.dp))
            }
          }
        }
      }
    }
  }
}
