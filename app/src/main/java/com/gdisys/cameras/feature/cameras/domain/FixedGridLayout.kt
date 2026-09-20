package com.gdisys.cameras.feature.cameras.domain

/** Proporção usada enquanto o primeiro frame do stream não chega. */
const val DEFAULT_STREAM_ASPECT_RATIO = 16f / 9f

/** Tamanho de um vídeo dentro da célula, já respeitando a proporção real. */
data class StreamCellSize(val width: Float, val height: Float)

/**
 * Altura de cada linha da grade fixa.
 *
 * A largura da célula é fixa (a tela dividida pelas colunas), então a altura que cada vídeo pede é
 * `largura / proporção`; a linha usa a **maior** delas, que é a da maior resolução da linha. Se a
 * soma das alturas não couber em [availableHeight], todas são **escaladas uniformemente** — o vídeo
 * encolhe, mas não é cortado nem distorcido, e não há scroll.
 *
 * As unidades são livres (px ou dp), desde que todas as entradas usem a mesma.
 *
 * @param aspectRatios proporção de cada stream da página, em *row-major*
 */
fun fixedGridRowHeights(
  aspectRatios: List<Float>,
  columns: Int,
  cellWidth: Float,
  availableHeight: Float,
  verticalSpacing: Float
): List<Float> {
  if (aspectRatios.isEmpty() || columns <= 0 || cellWidth <= 0f) return emptyList()

  val naturalHeights = aspectRatios.chunked(columns).map { row ->
    row.maxOf { aspectRatio -> cellWidth / aspectRatio.coerceAtLeast(MIN_ASPECT_RATIO) }
  }

  val totalSpacing = verticalSpacing * (naturalHeights.size - 1)
  val totalHeight = naturalHeights.sum()
  val room = availableHeight - totalSpacing
  if (totalHeight <= room) return naturalHeights

  val scale = (room / totalHeight).coerceAtLeast(0f)
  return naturalHeights.map { it * scale }
}

/**
 * Maior tamanho com a proporção [aspectRatio] que cabe numa célula de [cellWidth] × [cellHeight].
 *
 * É o que garante "sem distorção e sem corte": a célula pode sobrar nas laterais ou em cima/embaixo,
 * o vídeo nunca é esticado.
 */
fun fittedCellSize(cellWidth: Float, cellHeight: Float, aspectRatio: Float): StreamCellSize {
  val ratio = aspectRatio.coerceAtLeast(MIN_ASPECT_RATIO)
  val width = minOf(cellWidth, cellHeight * ratio).coerceAtLeast(0f)
  return StreamCellSize(width = width, height = width / ratio)
}

private const val MIN_ASPECT_RATIO = 0.01f
