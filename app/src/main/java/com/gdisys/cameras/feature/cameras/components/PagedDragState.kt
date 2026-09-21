package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.gdisys.cameras.feature.cameras.logic.movedToPage
import com.gdisys.cameras.feature.cameras.logic.reorderedTo

/**
 * Identifica uma célula pela página em que está.
 *
 * A URL sozinha não serve: ao cruzar a fronteira de página a mesma URL é composta na página nova
 * antes de a antiga ser descartada, e a limpeza tardia da antiga apagaria os limites recém
 * registrados pela nova.
 */
internal data class CellKey(val page: Int, val url: String)

/**
 * Holds the drag/reorder gesture state for [PagedStreamGrid] — touch coordinates and layout
 * bounds, updated every frame. This is View state, not ViewModel state: taking it to the
 * ViewModel would be an MVVM mistake, not a fix.
 *
 * The pure reordering math lives in `feature/cameras/logic/StreamPaging.kt` and is covered by
 * `StreamPagingTest`; this class is only the mutable holder around it, so [PagedStreamGrid] is
 * left with just the `HorizontalPager` and `FixedGrid` wiring.
 */
internal class PagedDragState(initialStreams: List<String>) {
  var localOrder by mutableStateOf(initialStreams)
    private set
  var draggedUrl by mutableStateOf<String?>(null)
    private set
  var dragPositionInRoot by mutableStateOf(Offset.Zero)
    private set
  var draggedSize by mutableStateOf(Size.Zero)
    private set
  var edgeDirection by mutableIntStateOf(0)
    private set
  var containerBounds by mutableStateOf(Rect.Zero)

  val cellBounds = mutableStateMapOf<CellKey, Rect>()
  val handleBounds = mutableStateMapOf<CellKey, Rect>()

  private var orderChanged = false

  /** Fora do arraste a fonte de verdade é o storage; durante ele, o que o usuário está montando. */
  fun syncFromStorage(streams: List<String>) {
    if (draggedUrl == null) localOrder = streams
  }

  fun handleAt(positionInRoot: Offset): CellKey? =
    handleBounds.entries.firstOrNull { it.value.contains(positionInRoot) }?.key

  fun startDrag(key: CellKey) {
    val cell = cellBounds[key] ?: return
    draggedUrl = key.url
    dragPositionInRoot = cell.center
    draggedSize = cell.size
    orderChanged = false
  }

  fun dragBy(delta: Offset, edgeZonePx: Float) {
    val url = draggedUrl ?: return
    val position = dragPositionInRoot + delta
    dragPositionInRoot = position

    val hovered = cellBounds.entries
      .firstOrNull { (key, bounds) -> key.url != url && bounds.contains(position) }
    if (hovered != null) {
      val targetIndex = localOrder.indexOf(hovered.key.url)
      if (targetIndex >= 0) {
        localOrder = reorderedTo(localOrder, url, targetIndex)
        orderChanged = true
      }
    }

    edgeDirection = when {
      containerBounds.width <= 0f -> 0
      position.x <= containerBounds.left + edgeZonePx -> -1
      position.x >= containerBounds.right - edgeZonePx -> 1
      else -> 0
    }
  }

  /** Segurar o item na borda esquerda/direita avança a página e leva o item junto (D13). */
  fun advanceToPage(targetPage: Int, itemsPerPage: Int, atStart: Boolean) {
    val url = draggedUrl ?: return
    localOrder = movedToPage(localOrder, url, targetPage, itemsPerPage, atStart = atStart)
    orderChanged = true
  }

  fun finishDrag(onReordered: (List<String>) -> Unit) {
    if (draggedUrl == null) return
    draggedUrl = null
    edgeDirection = 0
    draggedSize = Size.Zero
    // Um toque no handle, sem arrastar nada, não deve gerar escrita no storage.
    if (orderChanged) onReordered(localOrder)
    orderChanged = false
  }

  fun onCellDisposed(key: CellKey) {
    cellBounds.remove(key)
    handleBounds.remove(key)
  }
}

@Composable
internal fun rememberPagedDragState(streams: List<String>): PagedDragState {
  val state = remember { PagedDragState(streams) }
  LaunchedEffect(streams) { state.syncFromStorage(streams) }
  return state
}
