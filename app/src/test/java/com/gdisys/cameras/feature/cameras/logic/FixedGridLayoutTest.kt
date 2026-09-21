package com.gdisys.cameras.feature.cameras.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedGridLayoutTest {

  private val tolerance = 0.001f

  private val wide = 16f / 9f
  private val squareish = 4f / 3f
  private val portrait = 9f / 16f

  @Test
  fun `a single row uses the height asked by its aspect ratio`() {
    val heights = fixedGridRowHeights(
      aspectRatios = listOf(wide),
      columns = 1,
      cellWidth = 160f,
      availableHeight = 1000f,
      verticalSpacing = 8f
    )

    assertEquals(1, heights.size)
    assertEquals(90f, heights.single(), tolerance)
  }

  @Test
  fun `the row height is the one of its tallest video`() {
    // At the same width 4:3 is taller than 16:9, so it is the one that sets the row height.
    val heights = fixedGridRowHeights(
      aspectRatios = listOf(wide, squareish),
      columns = 2,
      cellWidth = 120f,
      availableHeight = 1000f,
      verticalSpacing = 8f
    )

    assertEquals(1, heights.size)
    assertEquals(90f, heights.single(), tolerance)
  }

  @Test
  fun `rows are filled row-major and the last one may be incomplete`() {
    val heights = fixedGridRowHeights(
      aspectRatios = listOf(wide, wide, portrait),
      columns = 2,
      cellWidth = 90f,
      availableHeight = 1000f,
      verticalSpacing = 8f
    )

    assertEquals(2, heights.size)
    assertEquals(50.625f, heights[0], tolerance)
    assertEquals(160f, heights[1], tolerance)
  }

  @Test
  fun `heights are scaled uniformly when they do not fit`() {
    val availableHeight = 100f
    val spacing = 10f

    val heights = fixedGridRowHeights(
      aspectRatios = listOf(wide, wide, wide),
      columns = 1,
      cellWidth = 160f,
      availableHeight = availableHeight,
      verticalSpacing = spacing
    )

    assertEquals(3, heights.size)
    // Uniform scaling: the three rows stay equal to each other and the set fits the screen.
    assertEquals(heights[0], heights[1], tolerance)
    assertEquals(heights[1], heights[2], tolerance)
    assertEquals(availableHeight, heights.sum() + spacing * 2, tolerance)
  }

  @Test
  fun `scaling preserves the proportion between rows of different resolutions`() {
    val heights = fixedGridRowHeights(
      aspectRatios = listOf(wide, portrait),
      columns = 1,
      cellWidth = 90f,
      availableHeight = 105f,
      verticalSpacing = 5f
    )

    // Natural heights: 50.625 and 160 — the ratio between them survives the shrinking.
    assertEquals(160f / 50.625f, heights[1] / heights[0], tolerance)
    assertEquals(105f, heights.sum() + 5f, tolerance)
  }

  @Test
  fun `no scaling happens when everything already fits`() {
    val heights = fixedGridRowHeights(
      aspectRatios = listOf(wide, wide),
      columns = 1,
      cellWidth = 160f,
      availableHeight = 1000f,
      verticalSpacing = 8f
    )

    assertEquals(listOf(90f, 90f), heights)
  }

  @Test
  fun `degenerate inputs produce no rows`() {
    assertTrue(fixedGridRowHeights(emptyList(), 2, 100f, 100f, 8f).isEmpty())
    assertTrue(fixedGridRowHeights(listOf(wide), 0, 100f, 100f, 8f).isEmpty())
    assertTrue(fixedGridRowHeights(listOf(wide), 2, 0f, 100f, 8f).isEmpty())
  }

  @Test
  fun `fittedCellSize fills the width when the cell is tall enough`() {
    val size = fittedCellSize(cellWidth = 160f, cellHeight = 200f, aspectRatio = wide)

    assertEquals(160f, size.width, tolerance)
    assertEquals(90f, size.height, tolerance)
  }

  @Test
  fun `fittedCellSize fills the height when the cell is too short`() {
    val size = fittedCellSize(cellWidth = 160f, cellHeight = 45f, aspectRatio = wide)

    assertEquals(80f, size.width, tolerance)
    assertEquals(45f, size.height, tolerance)
  }

  @Test
  fun `fittedCellSize never distorts the video`() {
    val size = fittedCellSize(cellWidth = 300f, cellHeight = 100f, aspectRatio = portrait)

    assertEquals(portrait, size.width / size.height, tolerance)
    assertTrue(size.width <= 300f)
    assertTrue(size.height <= 100f)
  }
}
