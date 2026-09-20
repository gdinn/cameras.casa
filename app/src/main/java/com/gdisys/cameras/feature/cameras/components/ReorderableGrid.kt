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
 * Grade de streams reordenável por arraste.
 *
 * O item só é persistido ao ser **solto**: durante o arraste a nova ordem vive em [localOrder], o
 * que faz o item acompanhar o dedo sem esperar o round-trip pelo storage. O auto-scroll vertical
 * ao segurar o item nas bordas vem pronto da lib (decisão P5/P6).
 *
 * O conteúdo de cada célula recebe o `Modifier` do *handle* de arraste em vez do escopo da lib, de
 * modo que os composables de item não dependem dela. É também onde a etapa 4 vai pendurar o
 * auto-avanço de página nas bordas horizontais.
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

  // Fora do arraste a fonte de verdade é o storage; durante ele, o que o usuário está montando.
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
