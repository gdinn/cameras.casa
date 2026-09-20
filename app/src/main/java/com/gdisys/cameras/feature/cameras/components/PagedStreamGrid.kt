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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import com.gdisys.cameras.feature.cameras.logic.movedToPage
import com.gdisys.cameras.feature.cameras.logic.pageCount
import com.gdisys.cameras.feature.cameras.logic.reorderedTo
import com.gdisys.cameras.feature.cameras.logic.streamsOnPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PAGE_EDGE_ADVANCE_DELAY_MS = 600L
private const val DRAGGED_CELL_ALPHA = 0.3f
private val PAGE_EDGE_ZONE = 48.dp
private val GRID_PADDING = 8.dp

/**
 * Identifica uma célula pela página em que está.
 *
 * A URL sozinha não serve: ao cruzar a fronteira de página a mesma URL é composta na página nova
 * antes de a antiga ser descartada, e a limpeza tardia da antiga apagaria os limites recém
 * registrados pela nova.
 */
private data class CellKey(val page: Int, val url: String)

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

  var localOrder by remember { mutableStateOf(streams) }
  var draggedUrl by remember { mutableStateOf<String?>(null) }
  var dragPositionInRoot by remember { mutableStateOf(Offset.Zero) }
  var draggedSize by remember { mutableStateOf(Size.Zero) }
  var edgeDirection by remember { mutableIntStateOf(0) }
  var orderChanged by remember { mutableStateOf(false) }
  var containerBounds by remember { mutableStateOf(Rect.Zero) }
  val cellBounds = remember { mutableStateMapOf<CellKey, Rect>() }
  val handleBounds = remember { mutableStateMapOf<CellKey, Rect>() }
  val currentOnStreamsReordered by rememberUpdatedState(onStreamsReordered)

  // Fora do arraste a fonte de verdade é o storage; durante ele, o que o usuário está montando.
  LaunchedEffect(streams) {
    if (draggedUrl == null) localOrder = streams
  }

  val pages = pageCount(localOrder.size, perPage)
  val pagerState = rememberPagerState(pageCount = { pages })

  fun startDrag(url: String, cell: Rect) {
    draggedUrl = url
    dragPositionInRoot = cell.center
    draggedSize = cell.size
    orderChanged = false
  }

  fun dragBy(delta: Offset) {
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

  fun finishDrag() {
    if (draggedUrl == null) return
    draggedUrl = null
    edgeDirection = 0
    draggedSize = Size.Zero
    // Um toque no handle, sem arrastar nada, não deve gerar escrita no storage.
    if (orderChanged) currentOnStreamsReordered(localOrder)
    orderChanged = false
  }

  // Segurar o item na borda esquerda/direita avança a página e leva o item junto (D13).
  LaunchedEffect(edgeDirection, pages, perPage) {
    if (edgeDirection == 0) return@LaunchedEffect
    while (true) {
      delay(PAGE_EDGE_ADVANCE_DELAY_MS)
      val url = draggedUrl ?: break
      val target = pagerState.currentPage + edgeDirection
      if (target !in 0 until pages) break
      localOrder = movedToPage(localOrder, url, target, perPage, atStart = edgeDirection > 0)
      orderChanged = true
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
      val downInRoot = containerBounds.topLeft + down.position
      val grabbed = handleBounds.entries.firstOrNull { it.value.contains(downInRoot) }
        ?: return@awaitEachGesture // fora de um handle: é toque de foco, não arraste
      val cell = cellBounds[grabbed.key] ?: return@awaitEachGesture

      down.consume()
      startDrag(grabbed.key.url, cell)

      try {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val change = event.changes.firstOrNull { it.id == down.id } ?: break
          if (!change.pressed) break
          val delta = change.positionChange()
          if (delta != Offset.Zero) {
            change.consume()
            dragBy(delta)
          }
        }
      } finally {
        // Também cobre o cancelamento do gesto: sem isso o fantasma ficaria preso na tela.
        finishDrag()
      }
    }
  }

  Column(modifier = modifier) {
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .onGloballyPositioned { containerBounds = it.boundsInRoot() }
        .then(dragGesture)
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 0,
        userScrollEnabled = draggedUrl == null
      ) { page ->
        FixedGrid(
          streams = streamsOnPage(localOrder, page, perPage),
          columns = grid.columns,
          orientation = orientation,
          aspectRatioOf = aspectRatioOf,
          modifier = Modifier
            .fillMaxSize()
            .padding(GRID_PADDING),
          onCellBounds = { url, bounds -> cellBounds[CellKey(page, url)] = bounds }
        ) { url, cellModifier ->
          val key = CellKey(page, url)
          // A página que sai leva embora os limites das suas células, senão o teste de colisão do
          // arraste acertaria retângulos de uma página que não está mais na tela.
          DisposableEffect(key) {
            onDispose {
              cellBounds.remove(key)
              handleBounds.remove(key)
            }
          }
          itemContent(
            url,
            Modifier.onGloballyPositioned { handleBounds[key] = it.boundsInRoot() },
            cellModifier.alpha(if (url == draggedUrl) DRAGGED_CELL_ALPHA else 1f)
          )
        }
      }

      DraggedStreamGhost(
        isVisible = draggedUrl != null,
        size = draggedSize,
        positionInRoot = dragPositionInRoot,
        containerTopLeft = containerBounds.topLeft
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
