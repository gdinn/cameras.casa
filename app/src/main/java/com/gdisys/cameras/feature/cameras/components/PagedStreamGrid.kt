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
 * Modo fixo: a grade de cada página é uma [FixedGrid] e as páginas trocam por swipe, pelos botões
 * anterior/próximo ou pelo auto-avanço do arraste.
 *
 * Só a página corrente fica composta (`beyondViewportPageCount = 0`), então a conexão WHEP dos
 * streams das outras páginas cai sozinha pelo `onDispose` do [WebRtcVideoPlayer] — não há tracking
 * de visibilidade em lugar nenhum.
 *
 * O arraste é próprio (a lib de reorder do modo dinâmico só funciona sobre listas *lazy*) e o
 * detector vive no **contêiner**, não no *handle*: durante o arraste entre páginas a célula de
 * origem é descartada, e um gesto ancorado nela morreria junto. O contêiner sobrevive a qualquer
 * troca de página, então o gesto acompanha o dedo até o fim.
 *
 * O item arrastado é representado por um fantasma flutuante, para que o player de verdade continue
 * na célula. Ao soltar, a ordem completa da orientação corrente é persistida de uma vez.
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

  // Segurar o item na borda esquerda/direita avança a página e leva o item junto (D13).
  LaunchedEffect(dragState.edgeDirection, pages, perPage) {
    if (dragState.edgeDirection == 0) return@LaunchedEffect
    while (true) {
      delay(PAGE_EDGE_ADVANCE_DELAY_MS)
      if (dragState.draggedUrl == null) break
      val target = pagerState.currentPage + dragState.edgeDirection
      if (target !in 0 until pages) break
      dragState.advanceToPage(target, perPage, atStart = dragState.edgeDirection > 0)
      // A rolagem roda num escopo próprio e só é aguardada aqui: soltar o item zera a direção e
      // cancela este efeito, e uma animação interrompida no meio deixaria o pager parado entre
      // duas páginas.
      scope.launch { pagerState.animateScrollToPage(target) }.join()
    }
  }

  // O gesto nasce no contêiner e usa a passada Initial: assim ele ganha do scroll horizontal do
  // pager, que é filho, sem depender de quem consome o quê depois.
  val dragGesture = Modifier.pointerInput(perPage) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      val downInRoot = dragState.containerBounds.topLeft + down.position
      val grabbed = dragState.handleAt(downInRoot)
        ?: return@awaitEachGesture // fora de um handle: é toque de foco, não arraste

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
        // Também cobre o cancelamento do gesto: sem isso o fantasma ficaria preso na tela.
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
          // A página que sai leva embora os limites das suas células, senão o teste de colisão do
          // arraste acertaria retângulos de uma página que não está mais na tela.
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

/** Fantasma que acompanha o dedo; o player de verdade fica na célula, ainda conectado. */
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

/** Anterior/próximo e o contador de páginas; o swipe cobre o mesmo caminho. */
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
