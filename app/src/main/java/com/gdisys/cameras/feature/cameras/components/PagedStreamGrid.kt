package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.feature.cameras.logic.itemsPerPage
import com.gdisys.cameras.feature.cameras.logic.pageCount
import com.gdisys.cameras.feature.cameras.logic.streamsOnPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PAGE_EDGE_ADVANCE_DELAY_MS = 600L
private const val DRAGGED_CELL_ALPHA = 0.3f
private val PAGE_EDGE_ZONE = 48.dp
private val GRID_PADDING = 8.dp

/**
 * Fixed mode: each page's grid is a [FixedGrid], and pages change by swiping, by the
 * previous/next buttons, or by the drag auto-advancing them.
 *
 * Only the current page stays composed (`beyondViewportPageCount = 0`), so the WHEP connections of
 * the other pages' streams drop by themselves through [WebRtcVideoPlayer]'s `onDispose` — there is
 * no visibility tracking anywhere.
 *
 * The drag is hand-written, because the reorder library used in dynamic mode only works over *lazy*
 * lists, and the detector lives on the **container**, not on the *handle*: dragging across pages
 * discards the originating cell, and a gesture anchored to it would die with it. The container
 * survives every page change, so the gesture follows the finger to the end.
 *
 * The dragged item is drawn as a floating ghost, so the real player stays in its cell. On drop, the
 * current orientation's complete order is persisted in one write.
 */
@Composable
fun PagedStreamGrid(
  streams: List<String>,
  grid: GridPreferences,
  orientation: StreamOrientation,
  aspectRatioOf: (String) -> Float,
  onStreamsReordered: (List<String>) -> Unit,
  modifier: Modifier = Modifier,
  itemContent: @Composable (url: String, dragHandleModifier: Modifier, cellModifier: Modifier) -> Unit
) {
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val edgeZonePx = with(density) { PAGE_EDGE_ZONE.toPx() }
  val perPage = itemsPerPage(grid.rows, grid.columns)

  val dragState = rememberPagedDragState(streams)
  val currentOnStreamsReordered by rememberUpdatedState(onStreamsReordered)

  val pages = pageCount(dragState.localOrder.size, perPage)
  val pagerState = rememberPagerState(pageCount = { pages })

  // Holding the item against the left/right edge advances the page and takes the item along.
  LaunchedEffect(dragState.edgeDirection, pages, perPage) {
    if (dragState.edgeDirection == 0) return@LaunchedEffect
    while (true) {
      delay(PAGE_EDGE_ADVANCE_DELAY_MS)
      if (dragState.draggedUrl == null) break
      val target = pagerState.currentPage + dragState.edgeDirection
      if (target !in 0 until pages) break
      dragState.advanceToPage(target, perPage, atStart = dragState.edgeDirection > 0)
      // The scroll runs in its own scope and is only awaited here: dropping the item zeroes the
      // direction and cancels this effect, and an animation interrupted halfway would leave the
      // pager stranded between two pages.
      scope.launch { pagerState.animateScrollToPage(target) }.join()
    }
  }

  // The gesture starts on the container and uses the Initial pass, so it wins over the pager's
  // horizontal scroll — a child — without depending on who consumes what afterwards.
  val dragGesture = Modifier.pointerInput(perPage) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      val downInRoot = dragState.containerBounds.topLeft + down.position
      val grabbed = dragState.handleAt(downInRoot)
        ?: return@awaitEachGesture // outside a handle: this is a focus tap, not a drag

      down.consume()
      dragState.startDrag(grabbed)

      try {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val change = event.changes.firstOrNull { it.id == down.id } ?: break
          if (!change.pressed) break
          val delta = change.positionChange()
          if (delta != Offset.Zero) {
            change.consume()
            dragState.dragBy(delta, edgeZonePx)
          }
        }
      } finally {
        // Also covers gesture cancellation: without this the ghost would stay stuck on screen.
        dragState.finishDrag(currentOnStreamsReordered)
      }
    }
  }

  Column(modifier = modifier) {
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .onGloballyPositioned { dragState.containerBounds = it.boundsInRoot() }
        .then(dragGesture)
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 0,
        userScrollEnabled = dragState.draggedUrl == null
      ) { page ->
        FixedGrid(
          streams = streamsOnPage(dragState.localOrder, page, perPage),
          columns = grid.columns,
          orientation = orientation,
          aspectRatioOf = aspectRatioOf,
          modifier = Modifier
            .fillMaxSize()
            .padding(GRID_PADDING),
          onCellBounds = { url, bounds -> dragState.cellBounds[CellKey(page, url)] = bounds }
        ) { url, cellModifier ->
          val key = CellKey(page, url)
          // A page being left takes its cells' bounds with it, or the drag's hit test would keep
          // matching rectangles from a page that is no longer on screen.
          DisposableEffect(key) {
            onDispose { dragState.onCellDisposed(key) }
          }
          itemContent(
            url,
            Modifier.onGloballyPositioned { dragState.handleBounds[key] = it.boundsInRoot() },
            cellModifier.alpha(if (url == dragState.draggedUrl) DRAGGED_CELL_ALPHA else 1f)
          )
        }
      }

      DraggedStreamGhost(
        isVisible = dragState.draggedUrl != null,
        size = dragState.draggedSize,
        positionInRoot = dragState.dragPositionInRoot,
        containerTopLeft = dragState.containerBounds.topLeft
      )
    }

    if (pages > 1) {
      PagerControls(
        currentPage = pagerState.currentPage,
        pageCount = pages,
        onGoToPage = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
      )
    }
  }
}

/** Ghost that follows the finger; the real player stays in its cell, still connected. */
@Composable
private fun DraggedStreamGhost(
  isVisible: Boolean,
  size: Size,
  positionInRoot: Offset,
  containerTopLeft: Offset
) {
  if (!isVisible || size.width <= 0f || size.height <= 0f) return

  val density = LocalDensity.current
  Box(
    modifier = Modifier
      .offset {
        val topLeft = positionInRoot - containerTopLeft - Offset(size.width / 2f, size.height / 2f)
        IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
      }
      .size(
        width = with(density) { size.width.toDp() },
        height = with(density) { size.height.toDp() }
      )
      .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
      .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = Icons.Default.DragHandle,
      contentDescription = stringResource(R.string.home_screen_drag_handle),
      tint = Color.White
    )
  }
}

/** Previous/next and the page counter; swiping covers the same ground. */
@Composable
private fun PagerControls(currentPage: Int, pageCount: Int, onGoToPage: (Int) -> Unit) {
  val hasPrevious = currentPage > 0
  val hasNext = currentPage < pageCount - 1

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = { onGoToPage(currentPage - 1) }, enabled = hasPrevious) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        contentDescription = stringResource(R.string.home_screen_previous_page),
        tint = pagerControlTint(hasPrevious)
      )
    }
    Text(
      text = stringResource(R.string.home_screen_page_indicator, currentPage + 1, pageCount),
      color = Color.White
    )
    IconButton(onClick = { onGoToPage(currentPage + 1) }, enabled = hasNext) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = stringResource(R.string.home_screen_next_page),
        tint = pagerControlTint(hasNext)
      )
    }
  }
}

private fun pagerControlTint(enabled: Boolean): Color =
  if (enabled) Color.White else Color.White.copy(alpha = 0.3f)
