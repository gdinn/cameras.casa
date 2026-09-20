package com.gdisys.cameras.feature.streamurls.logic

import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPreferencesValidationTest {

  @Test
  fun `accepts columns and rows inside the 1 to 6 range`() {
    assertEquals(
      GridPreferences(columns = 1, rows = 1, dynamicRows = false),
      validateGridPreferences(columns = "1", rows = "1", dynamicRows = false)
    )
    assertEquals(
      GridPreferences(columns = 6, rows = 6, dynamicRows = false),
      validateGridPreferences(columns = "6", rows = "6", dynamicRows = false)
    )
    assertEquals(
      GridPreferences(columns = 3, rows = 2, dynamicRows = false),
      validateGridPreferences(columns = "3", rows = "2", dynamicRows = false)
    )
  }

  @Test
  fun `rejects columns outside the range`() {
    assertNull(validateGridPreferences(columns = "0", rows = "2", dynamicRows = false))
    assertNull(validateGridPreferences(columns = "7", rows = "2", dynamicRows = false))
    assertNull(validateGridPreferences(columns = "-1", rows = "2", dynamicRows = false))
  }

  @Test
  fun `rejects rows outside the range when rows are fixed`() {
    assertNull(validateGridPreferences(columns = "2", rows = "0", dynamicRows = false))
    assertNull(validateGridPreferences(columns = "2", rows = "7", dynamicRows = false))
  }

  @Test
  fun `rejects empty or non numeric values`() {
    assertNull(validateGridPreferences(columns = "", rows = "2", dynamicRows = false))
    assertNull(validateGridPreferences(columns = "two", rows = "2", dynamicRows = false))
    assertNull(validateGridPreferences(columns = "2", rows = "", dynamicRows = false))
    assertNull(validateGridPreferences(columns = "2", rows = "two", dynamicRows = false))
    assertNull(validateGridPreferences(columns = "2.5", rows = "2", dynamicRows = false))
  }

  @Test
  fun `ignores an invalid rows field in dynamic mode`() {
    assertEquals(
      GridPreferences(columns = 2, rows = 1, dynamicRows = true),
      validateGridPreferences(columns = "2", rows = "", dynamicRows = true)
    )
    assertEquals(
      GridPreferences(columns = 2, rows = 1, dynamicRows = true),
      validateGridPreferences(columns = "2", rows = "99", dynamicRows = true)
    )
  }

  @Test
  fun `keeps a valid rows field in dynamic mode`() {
    assertEquals(
      GridPreferences(columns = 2, rows = 4, dynamicRows = true),
      validateGridPreferences(columns = "2", rows = "4", dynamicRows = true)
    )
  }

  @Test
  fun `columns are required even in dynamic mode`() {
    assertNull(validateGridPreferences(columns = "", rows = "2", dynamicRows = true))
  }

  @Test
  fun `showsAllStreams is true when the grid fits every stream`() {
    val grid = GridPreferences(columns = 2, rows = 2, dynamicRows = false)

    assertTrue(grid.showsAllStreams(streamCount = 4))
    assertTrue(grid.showsAllStreams(streamCount = 3))
  }

  @Test
  fun `showsAllStreams is false when the grid is smaller than the stream list`() {
    val grid = GridPreferences(columns = 2, rows = 2, dynamicRows = false)

    assertFalse(grid.showsAllStreams(streamCount = 5))
  }

  @Test
  fun `showsAllStreams is always true in dynamic mode`() {
    val grid = GridPreferences(columns = 1, rows = 1, dynamicRows = true)

    assertTrue(grid.showsAllStreams(streamCount = 20))
  }
}
