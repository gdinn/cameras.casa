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
import com.gdisys.cameras.feature.cameras.domain.fittedCellSize
import com.gdisys.cameras.feature.cameras.domain.fixedGridRowHeights

/**
 * Grade de linhas fixas: **sem scroll** e com medição própria.
 *
 * A largura da célula é a tela dividida pelas colunas; a altura de cada linha é a do vídeo de maior
 * resolução da linha, e o conjunto é escalado uniformemente quando não cabe na altura disponível
 * (ver `fixedGridRowHeights`). Sobra de espaço é esperada: em **Portrait** o conteúdo fica à
 * esquerda e ao topo; em **Landscape**, centralizado nos dois eixos.
 *
 * As células que sobram quando `linhas × colunas` é maior que o número de streams simplesmente não
 * são emitidas — como o preenchimento é *row-major*, elas estão sempre no fim.
 *
 * @param streams streams desta página, já na ordem da orientação corrente
 * @param onCellBounds posição de cada célula **na raiz**, usada pelo arraste da [PagedStreamGrid]
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
