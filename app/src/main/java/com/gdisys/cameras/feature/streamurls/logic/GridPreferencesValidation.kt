package com.gdisys.cameras.feature.streamurls.logic

import com.gdisys.cameras.core.storage.domain.model.GridPreferences

/** Accepted range for the grid's columns and rows, in both orientations. */
const val MIN_GRID_DIMENSION = 1
const val MAX_GRID_DIMENSION = 6

/**
 * Row count written when dynamic mode is on and the rows field holds no valid value.
 *
 * In dynamic mode the row count is ignored for display, but the model persists an integer anyway;
 * writing a value inside the accepted range keeps the preference coherent in case the user turns
 * dynamic mode off later.
 */
private const val FALLBACK_ROWS = MIN_GRID_DIMENSION

/**
 * Validates one orientation's grid fields and returns the matching preferences, or `null` when the
 * configuration is invalid — which is what disables the section's save button.
 *
 * Columns are always required. Rows are only required outside dynamic mode: with [dynamicRows] on
 * the field is ignored, so an empty or invalid value there does not block saving.
 */
fun validateGridPreferences(
  columns: String,
  rows: String,
  dynamicRows: Boolean
): GridPreferences? {
  val parsedColumns = columns.toIntOrNull()?.takeIf { it in MIN_GRID_DIMENSION..MAX_GRID_DIMENSION }
    ?: return null

  val parsedRows = rows.toIntOrNull()?.takeIf { it in MIN_GRID_DIMENSION..MAX_GRID_DIMENSION }
  if (!dynamicRows && parsedRows == null) return null

  return GridPreferences(
    columns = parsedColumns,
    rows = parsedRows ?: FALLBACK_ROWS,
    dynamicRows = dynamicRows
  )
}

/**
 * `false` when the grid is smaller than the number of registered streams — a valid configuration,
 * but one worth warning about, since some streams then never appear on screen.
 *
 * In dynamic mode the grid grows and scrolls, so it always shows every stream.
 */
fun GridPreferences.showsAllStreams(streamCount: Int): Boolean =
  dynamicRows || columns * rows >= streamCount
