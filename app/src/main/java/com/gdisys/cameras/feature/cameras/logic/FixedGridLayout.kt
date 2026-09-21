package com.gdisys.cameras.feature.cameras.logic

/** Ratio used until the stream's first frame arrives. */
const val DEFAULT_STREAM_ASPECT_RATIO = 16f / 9f

/** Size of a video inside its cell, already respecting the real aspect ratio. */
data class StreamCellSize(val width: Float, val height: Float)

/**
 * Altura de cada linha da grade fixa.
 *
 * Cell width is fixed (the screen divided by the column count), so the height each video asks for
 * is `width / ratio`; the row takes the **largest** of those, which is its tallest video. If the
 * heights do not add up to fit [availableHeight], all of them are **scaled uniformly** — the video
 * shrinks, but it is never cropped or distorted, and nothing scrolls.
 *
 * Units are up to the caller (px or dp), as long as every input uses the same one.
 *
 * @param aspectRatios each stream's ratio on the page, in *row-major* order
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
 * Largest size with ratio [aspectRatio] that fits a [cellWidth] x [cellHeight] cell.
 *
 * This is what guarantees "no distortion and no cropping": the cell may have space left over at the
 * sides or above and below, but the video is never stretched.
 */
fun fittedCellSize(cellWidth: Float, cellHeight: Float, aspectRatio: Float): StreamCellSize {
  val ratio = aspectRatio.coerceAtLeast(MIN_ASPECT_RATIO)
  val width = minOf(cellWidth, cellHeight * ratio).coerceAtLeast(0f)
  return StreamCellSize(width = width, height = width / ratio)
}

private const val MIN_ASPECT_RATIO = 0.01f
