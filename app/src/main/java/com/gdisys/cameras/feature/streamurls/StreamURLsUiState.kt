package com.gdisys.cameras.feature.streamurls

import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.feature.streamurls.logic.validateGridPreferences

/**
 * State of the Stream URLs screen.
 *
 * All editing happens in memory: [streamUrls] and both [GridInput]s reflect the user's draft, not
 * what is persisted. Nothing reaches storage before an explicit save.
 */
data class StreamURLsUiState(
  val isLoading: Boolean = true,
  val streamUrls: List<String> = emptyList(),
  val isAddFormVisible: Boolean = false,
  val portInput: String = "",
  val streamNameInput: String = "",
  val isUrlsSectionDirty: Boolean = false,
  val portraitGridInput: GridInput = GridInput(),
  val landscapeGridInput: GridInput = GridInput(),
  val isGridSectionDirty: Boolean = false,
  val dialog: StreamURLsDialog? = null
) {
  /** One URL at a time: `+` stays locked while the form is open. */
  val isAddUrlButtonEnabled: Boolean
    get() = !isAddFormVisible

  /** Saving the grid is only possible when both orientations are valid. */
  val isSaveGridButtonEnabled: Boolean
    get() = portraitGridInput.validated() != null && landscapeGridInput.validated() != null

  /** Union of every section's unsaved edits — this is what triggers the discard dialog. */
  val isDirty: Boolean
    get() = isUrlsSectionDirty || isGridSectionDirty
}

/**
 * One orientation's grid fields, as text — what the user types, incomplete or invalid values
 * included. Conversion to the model happens in [validated].
 */
data class GridInput(
  val columns: String = "",
  val rows: String = "",
  val dynamicRows: Boolean = true
) {
  /** The matching [GridPreferences], or `null` while the configuration is still invalid. */
  fun validated(): GridPreferences? = validateGridPreferences(columns, rows, dynamicRows)
}

/** Edit fields matching a persisted grid. */
fun GridPreferences.toInput(): GridInput = GridInput(
  columns = columns.toString(),
  rows = rows.toString(),
  dynamicRows = dynamicRows
)

/** The screen's confirmation dialogs; only one can be visible at a time. */
sealed interface StreamURLsDialog {
  /** Saving overwrites what is persisted. */
  data object ConfirmSaveUrls : StreamURLsDialog

  /** Saving the grid overwrites both persisted grids. */
  data object ConfirmSaveGrid : StreamURLsDialog

  /** The default configuration replaces the current list, in memory only. */
  data object ConfirmLoadDefaults : StreamURLsDialog

  /** Going back with unsaved changes. */
  data object ConfirmDiscard : StreamURLsDialog
}
