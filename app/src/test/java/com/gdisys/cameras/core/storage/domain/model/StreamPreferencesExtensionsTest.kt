package com.gdisys.cameras.core.storage.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamPreferencesExtensionsTest {

  @Test
  fun `keeps an order that is already a valid permutation`() {
    val preferences = StreamPreferences(
      streamUrls = listOf("a", "b", "c"),
      portraitOrder = listOf("c", "a", "b"),
      landscapeOrder = listOf("b", "c", "a")
    )

    val result = preferences.reconciled()

    assertEquals(listOf("c", "a", "b"), result.portraitOrder)
    assertEquals(listOf("b", "c", "a"), result.landscapeOrder)
  }

  @Test
  fun `drops urls that are no longer in the canonical set`() {
    val preferences = StreamPreferences(
      streamUrls = listOf("a", "c"),
      portraitOrder = listOf("c", "b", "a"),
      landscapeOrder = listOf("b", "a", "c")
    )

    val result = preferences.reconciled()

    assertEquals(listOf("c", "a"), result.portraitOrder)
    assertEquals(listOf("a", "c"), result.landscapeOrder)
  }

  @Test
  fun `appends missing urls at the end preserving the canonical order`() {
    val preferences = StreamPreferences(
      streamUrls = listOf("a", "b", "c", "d"),
      portraitOrder = listOf("c", "a"),
      landscapeOrder = listOf("d")
    )

    val result = preferences.reconciled()

    assertEquals(listOf("c", "a", "b", "d"), result.portraitOrder)
    assertEquals(listOf("d", "a", "b", "c"), result.landscapeOrder)
  }

  @Test
  fun `rebuilds empty orders from the canonical set`() {
    val preferences = StreamPreferences(streamUrls = listOf("a", "b", "c"))

    val result = preferences.reconciled()

    assertEquals(listOf("a", "b", "c"), result.portraitOrder)
    assertEquals(listOf("a", "b", "c"), result.landscapeOrder)
  }

  @Test
  fun `empties the orders when there are no canonical urls`() {
    val preferences = StreamPreferences(
      portraitOrder = listOf("a"),
      landscapeOrder = listOf("b")
    )

    val result = preferences.reconciled()

    assertEquals(emptyList<String>(), result.portraitOrder)
    assertEquals(emptyList<String>(), result.landscapeOrder)
  }

  @Test
  fun `deduplicates repeated urls inside an order`() {
    val preferences = StreamPreferences(
      streamUrls = listOf("a", "b"),
      portraitOrder = listOf("b", "b", "a"),
      landscapeOrder = listOf("a", "a")
    )

    val result = preferences.reconciled()

    assertEquals(listOf("b", "a"), result.portraitOrder)
    assertEquals(listOf("a", "b"), result.landscapeOrder)
  }

  @Test
  fun `leaves the canonical set and the grids untouched`() {
    val preferences = StreamPreferences(
      streamUrls = listOf("a", "b"),
      portraitOrder = listOf("b"),
      landscapeOrder = emptyList(),
      portraitGrid = GridPreferences(columns = 3, rows = 2, dynamicRows = false),
      landscapeGrid = GridPreferences(columns = 4, rows = 1, dynamicRows = true)
    )

    val result = preferences.reconciled()

    assertEquals(preferences.streamUrls, result.streamUrls)
    assertEquals(preferences.portraitGrid, result.portraitGrid)
    assertEquals(preferences.landscapeGrid, result.landscapeGrid)
  }

  @Test
  fun `default grids are portrait 1 column dynamic and landscape 2 columns dynamic`() {
    val preferences = StreamPreferences()

    assertEquals(GridPreferences(columns = 1, rows = 1, dynamicRows = true), preferences.portraitGrid)
    assertEquals(GridPreferences(columns = 2, rows = 1, dynamicRows = true), preferences.landscapeGrid)
  }
}
