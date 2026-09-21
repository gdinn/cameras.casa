package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * Stream grid reorderable by dragging.
 *
 * The order is only persisted on **drop**: during the drag the new order lives in [localOrder], so
 * the item follows the finger without waiting for a round-trip through storage. Vertical
 * auto-scrolling when the item is held against an edge comes from the library.
 *
 * Each cell's content receives the drag *handle*'s `Modifier` rather than the library's own scope,
 * so the item composables carry no dependency on the library.
 */
@Composable
fun ReorderableStreamGrid(
  streams: List<String>,
  columns: Int,
  gridState: LazyGridState,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(8.dp),
  onStreamsReordered: (List<String>) -> Unit,
  itemContent: @Composable (url: String, dragHandleModifier: Modifier) -> Unit
) {
  var localOrder by remember { mutableStateOf(streams) }
  var isDragging by remember { mutableStateOf(false) }

  // Outside a drag, storage is the source of truth; during one, what the user is arranging is.
  LaunchedEffect(streams) {
    if (!isDragging) localOrder = streams
  }

  val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
    val fromIndex = localOrder.indexOf(from.key)
    val toIndex = localOrder.indexOf(to.key)
    if (fromIndex >= 0 && toIndex >= 0) {
      localOrder = localOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
  }

  LazyVerticalGrid(
    state = gridState,
    columns = GridCells.Fixed(columns),
    contentPadding = contentPadding,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
  ) {
    items(localOrder, key = { it }) { url ->
      ReorderableItem(reorderableState, key = url) {
        val dragHandleModifier = Modifier.draggableHandle(
          onDragStarted = { isDragging = true },
          onDragStopped = {
            isDragging = false
            onStreamsReordered(localOrder)
          }
        )
        itemContent(url, dragHandleModifier)
      }
    }
  }
}
