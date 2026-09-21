package com.gdisys.cameras.feature.cameras.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamPagingTest {

  private val streams = listOf("a", "b", "c", "d", "e", "f", "g")

  @Test
  fun `itemsPerPage multiplies rows by columns`() {
    assertEquals(6, itemsPerPage(rows = 2, columns = 3))
    assertEquals(1, itemsPerPage(rows = 1, columns = 1))
  }

  @Test
  fun `itemsPerPage never returns zero`() {
    assertEquals(1, itemsPerPage(rows = 0, columns = 0))
    assertEquals(1, itemsPerPage(rows = -2, columns = 3))
  }

  @Test
  fun `pageCount rounds up and is at least one`() {
    assertEquals(1, pageCount(streamCount = 0, itemsPerPage = 4))
    assertEquals(1, pageCount(streamCount = 4, itemsPerPage = 4))
    assertEquals(2, pageCount(streamCount = 5, itemsPerPage = 4))
    assertEquals(2, pageCount(streamCount = 7, itemsPerPage = 4))
    assertEquals(3, pageCount(streamCount = 9, itemsPerPage = 4))
  }

  @Test
  fun `streamsOnPage splits the list in row-major order`() {
    assertEquals(listOf("a", "b", "c", "d"), streamsOnPage(streams, page = 0, itemsPerPage = 4))
    assertEquals(listOf("e", "f", "g"), streamsOnPage(streams, page = 1, itemsPerPage = 4))
  }

  @Test
  fun `streamsOnPage returns nothing past the last page`() {
    assertEquals(emptyList<String>(), streamsOnPage(streams, page = 2, itemsPerPage = 4))
    assertEquals(emptyList<String>(), streamsOnPage(streams, page = -1, itemsPerPage = 4))
  }

  @Test
  fun `reorderedTo moves an item forward`() {
    assertEquals(
      listOf("b", "c", "a", "d"),
      reorderedTo(listOf("a", "b", "c", "d"), url = "a", targetIndex = 2)
    )
  }

  @Test
  fun `reorderedTo moves an item backward`() {
    assertEquals(
      listOf("a", "d", "b", "c"),
      reorderedTo(listOf("a", "b", "c", "d"), url = "d", targetIndex = 1)
    )
  }

  @Test
  fun `reorderedTo coerces an index outside the list`() {
    val order = listOf("a", "b", "c")

    assertEquals(listOf("b", "c", "a"), reorderedTo(order, url = "a", targetIndex = 9))
    assertEquals(listOf("c", "a", "b"), reorderedTo(order, url = "c", targetIndex = -3))
  }

  @Test
  fun `reorderedTo keeps the list when the url is unknown`() {
    val order = listOf("a", "b", "c")

    assertEquals(order, reorderedTo(order, url = "z", targetIndex = 0))
  }

  @Test
  fun `movedToPage drops the item at the start of the next page`() {
    // Dragging "a" (page 0) to the right edge: it enters page 1 from the left, which becomes
    // ["a", "f", "g"] — page 0 fills up with the four that were left over.
    assertEquals(
      listOf("b", "c", "d", "e", "a", "f", "g"),
      movedToPage(streams, url = "a", page = 1, itemsPerPage = 4, atStart = true)
    )
  }

  @Test
  fun `movedToPage drops the item at the end of the previous page`() {
    // Dragging "g" (page 1) to the left edge: it enters page 0 from the right.
    assertEquals(
      listOf("a", "b", "c", "g", "d", "e", "f"),
      movedToPage(streams, url = "g", page = 0, itemsPerPage = 4, atStart = false)
    )
  }

  @Test
  fun `movedToPage coerces a page that goes past the list`() {
    assertEquals(
      listOf("b", "c", "d", "e", "f", "g", "a"),
      movedToPage(streams, url = "a", page = 5, itemsPerPage = 4, atStart = true)
    )
  }

  @Test
  fun `movedToPage keeps the list when the url is unknown`() {
    assertEquals(streams, movedToPage(streams, url = "z", page = 1, itemsPerPage = 4, atStart = true))
  }
}
