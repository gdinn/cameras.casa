package com.gdisys.cameras.feature.cameras.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val EDGE_ZONE_PX = 20f

/**
 * The fixed grid's drag logic without a gesture: the pointer loop in `PagedStreamGrid` only feeds
 * these calls. Layout used throughout: a 400×400 container with a 2×2 page of 200×200 cells.
 *
 * ```
 * a | b
 * --+--
 * c | d
 * ```
 */
class PagedDragStateTest {

  private val streams = listOf("a", "b", "c", "d")
  private lateinit var state: PagedDragState

  @Before
  fun setUp() {
    state = PagedDragState(streams)
    state.containerBounds = Rect(0f, 0f, 400f, 400f)
    streams.forEachIndexed { index, url ->
      val left = (index % 2) * 200f
      val top = (index / 2) * 200f
      val key = CellKey(page = 0, url = url)
      state.cellBounds[key] = Rect(left, top, left + 200f, top + 200f)
      // The handle sits in the cell's top-end corner, as in CameraGridItem.
      state.handleBounds[key] = Rect(left + 160f, top, left + 200f, top + 40f)
    }
  }

  @Test
  fun `handleAt finds the handle under the finger and nothing elsewhere`() {
    assertEquals(CellKey(0, "b"), state.handleAt(Offset(380f, 20f)))
    assertNull(state.handleAt(Offset(100f, 100f)))
  }

  @Test
  fun `startDrag picks up the cell from its center with its size`() {
    state.startDrag(CellKey(0, "a"))

    assertEquals("a", state.draggedUrl)
    assertEquals(Offset(100f, 100f), state.dragPositionInRoot)
    assertEquals(Size(200f, 200f), state.draggedSize)
  }

  @Test
  fun `startDrag on a cell with no bounds does nothing`() {
    state.startDrag(CellKey(1, "a"))

    assertNull(state.draggedUrl)
  }

  @Test
  fun `dragging over another cell moves the item to its place`() {
    state.startDrag(CellKey(0, "a"))

    state.dragBy(Offset(200f, 0f), EDGE_ZONE_PX)

    assertEquals(Offset(300f, 100f), state.dragPositionInRoot)
    assertEquals(listOf("b", "a", "c", "d"), state.localOrder)
  }

  @Test
  fun `dragging within the item's own cell keeps the order`() {
    state.startDrag(CellKey(0, "a"))

    state.dragBy(Offset(30f, 30f), EDGE_ZONE_PX)

    assertEquals(streams, state.localOrder)
  }

  @Test
  fun `edgeDirection follows the finger into the left and right edge zones`() {
    state.startDrag(CellKey(0, "a"))

    state.dragBy(Offset(-90f, 0f), EDGE_ZONE_PX)
    assertEquals(-1, state.edgeDirection)

    state.dragBy(Offset(200f, 0f), EDGE_ZONE_PX)
    assertEquals(0, state.edgeDirection)

    state.dragBy(Offset(185f, 0f), EDGE_ZONE_PX)
    assertEquals(1, state.edgeDirection)
  }

  @Test
  fun `edgeDirection stays at zero before the container is measured`() {
    state.containerBounds = Rect.Zero
    state.startDrag(CellKey(0, "a"))

    state.dragBy(Offset(-95f, 0f), EDGE_ZONE_PX)

    assertEquals(0, state.edgeDirection)
  }

  @Test
  fun `advanceToPage takes the dragged item to the new page`() {
    state.startDrag(CellKey(0, "a"))

    state.advanceToPage(targetPage = 1, itemsPerPage = 2, atStart = true)

    assertEquals(listOf("b", "c", "a", "d"), state.localOrder)
  }

  @Test
  fun `finishDrag persists the new order once and resets the drag`() {
    val persisted = mutableListOf<List<String>>()
    state.startDrag(CellKey(0, "a"))
    state.dragBy(Offset(200f, 0f), EDGE_ZONE_PX)

    state.finishDrag { persisted += it }
    state.finishDrag { persisted += it }

    assertEquals(listOf(listOf("b", "a", "c", "d")), persisted)
    assertNull(state.draggedUrl)
    assertEquals(0, state.edgeDirection)
    assertEquals(Size.Zero, state.draggedSize)
  }

  @Test
  fun `a tap on the handle that moves nothing does not persist`() {
    val persisted = mutableListOf<List<String>>()
    state.startDrag(CellKey(0, "a"))

    state.finishDrag { persisted += it }

    assertEquals(emptyList<List<String>>(), persisted)
  }

  @Test
  fun `storage changes apply outside a drag and wait during one`() {
    state.syncFromStorage(listOf("d", "c", "b", "a"))
    assertEquals(listOf("d", "c", "b", "a"), state.localOrder)

    state.startDrag(CellKey(0, "a"))
    state.syncFromStorage(streams)
    assertEquals(listOf("d", "c", "b", "a"), state.localOrder)
  }

  @Test
  fun `a disposed cell no longer takes part in the hit tests`() {
    state.onCellDisposed(CellKey(0, "b"))

    assertNull(state.handleAt(Offset(380f, 20f)))
    state.startDrag(CellKey(0, "b"))
    assertNull(state.draggedUrl)
  }
}
