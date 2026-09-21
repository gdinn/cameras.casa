package com.gdisys.cameras.feature.streamurls

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.storage.domain.StreamUrlValidationResult
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveGridPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveStreamUrlsUseCase
import com.gdisys.cameras.core.storage.domain.validateStreamUrl
import com.gdisys.cameras.feature.streamurls.logic.showsAllStreams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreamURLsViewModel @Inject constructor(
  getStreamPreferencesUseCase: GetStreamPreferencesUseCase,
  private val saveStreamUrlsUseCase: SaveStreamUrlsUseCase,
  private val saveGridPreferencesUseCase: SaveGridPreferencesUseCase
) : ToastEventViewModel() {

  /**
   * Full preferences draft, seeded once from storage. It carries more than the UI shows (both
   * orders and both grids) precisely so that saving can rewrite the orders along with the
   * canonical set, without losing what this screen does not edit yet.
   *
   * This is the single source of truth for the URLs: what the screen shows is derived from here
   * by [uiState], so there is no mirrored copy to keep in sync.
   *
   * It is in-memory only, by decision: the draft does not survive process death. The screen
   * already confirms discarding on back, and restoring edits the user never saved would confuse
   * more than it would help.
   */
  private val draft = MutableStateFlow(StreamPreferences())

  /** State owned by the screen alone, which is not part of the persistable draft. */
  private val screenState = MutableStateFlow(StreamURLsScreenState())

  /**
   * Projection of the draft plus the screen state. `Eagerly` because the draft is the source of
   * truth for an edit in progress: it must stay reflected in the state even while the screen is
   * out of composition (configuration change, system dialog).
   */
  val uiState: StateFlow<StreamURLsUiState> =
    combine(draft, screenState) { preferences, screen -> screen.toUiState(preferences) }
      .stateIn(viewModelScope, SharingStarted.Eagerly, StreamURLsUiState())

  private val _navigateBackEvent = Channel<Unit>()
  val navigateBackEvent: Flow<Unit> = _navigateBackEvent.receiveAsFlow()

  init {
    viewModelScope.launch {
      // From here on the screen owns the draft: later storage changes do not overwrite it, or
      // the user would lose edits in progress.
      val storedPreferences = getStreamPreferencesUseCase().first()
      draft.value = storedPreferences
      screenState.update {
        it.copy(
          isLoading = false,
          portraitGridInput = storedPreferences.portraitGrid.toInput(),
          landscapeGridInput = storedPreferences.landscapeGrid.toInput()
        )
      }
    }
  }

  fun onShowAddUrlForm() {
    if (screenState.value.isAddFormVisible) return
    screenState.update { it.copy(isAddFormVisible = true, portInput = "", streamNameInput = "") }
  }

  fun onPortChanged(port: String) {
    screenState.update { it.copy(portInput = port) }
  }

  fun onStreamNameChanged(streamName: String) {
    screenState.update { it.copy(streamNameInput = streamName) }
  }

  fun onCancelAddUrlForm() {
    closeAddUrlForm()
  }

  fun onAddUrlConfirmed() {
    val screen = screenState.value
    val result = validateStreamUrl(
      port = screen.portInput,
      streamName = screen.streamNameInput,
      existingUrls = draft.value.streamUrls
    )

    when (result) {
      is StreamUrlValidationResult.Valid -> {
        // The URL joins the canonical set and the end of both orders.
        updateStreamUrls { preferences ->
          preferences.copy(
            streamUrls = preferences.streamUrls + result.url,
            portraitOrder = preferences.portraitOrder + result.url,
            landscapeOrder = preferences.landscapeOrder + result.url
          )
        }
        closeAddUrlForm()
      }

      StreamUrlValidationResult.InvalidPort ->
        showToast(StreamURLsToastMessage.INVALID_PORT)

      StreamUrlValidationResult.InvalidStreamName ->
        showToast(StreamURLsToastMessage.INVALID_STREAM_NAME)

      StreamUrlValidationResult.DuplicateUrl ->
        showToast(StreamURLsToastMessage.DUPLICATE_URL)
    }
  }

  fun onRemoveUrl(url: String) {
    updateStreamUrls { preferences ->
      preferences.copy(
        streamUrls = preferences.streamUrls - url,
        portraitOrder = preferences.portraitOrder - url,
        landscapeOrder = preferences.landscapeOrder - url
      )
    }
  }

  fun onLoadDefaultsRequested() {
    showDialog(StreamURLsDialog.ConfirmLoadDefaults)
  }

  fun onLoadDefaultsConfirmed() {
    dismissDialog()
    // Replaced in memory only: both orders restart in the defaults' own sequence.
    updateStreamUrls { preferences ->
      preferences.copy(
        streamUrls = StreamDefaults.CAMERA_STREAM_URLS,
        portraitOrder = StreamDefaults.CAMERA_STREAM_URLS,
        landscapeOrder = StreamDefaults.CAMERA_STREAM_URLS
      )
    }
    closeAddUrlForm()
  }

  fun onSaveUrlsRequested() {
    showDialog(StreamURLsDialog.ConfirmSaveUrls)
  }

  fun onSaveUrlsConfirmed() {
    dismissDialog()
    val preferences = draft.value
    viewModelScope.launch {
      saveStreamUrlsUseCase(
        streamUrls = preferences.streamUrls,
        portraitOrder = preferences.portraitOrder,
        landscapeOrder = preferences.landscapeOrder
      ).onSuccess {
        screenState.update { it.copy(isUrlsSectionDirty = false) }
        showToast(StreamURLsToastMessage.URLS_SAVED)
      }.onFailure { e ->
        Log.e(DEBUG_TAG, "Failed to save stream URLs", e)
        showToast(StreamURLsToastMessage.SAVE_URLS_ERROR)
      }
    }
  }

  fun onGridColumnsChanged(orientation: StreamOrientation, columns: String) {
    updateGridInput(orientation) { it.copy(columns = columns) }
  }

  fun onGridRowsChanged(orientation: StreamOrientation, rows: String) {
    updateGridInput(orientation) { it.copy(rows = rows) }
  }

  fun onGridDynamicRowsChanged(orientation: StreamOrientation, dynamicRows: Boolean) {
    updateGridInput(orientation) { it.copy(dynamicRows = dynamicRows) }
  }

  fun onSaveGridRequested() {
    showDialog(StreamURLsDialog.ConfirmSaveGrid)
  }

  fun onSaveGridConfirmed() {
    dismissDialog()
    val screen = screenState.value
    val portraitGrid = screen.portraitGridInput.validated()
    val landscapeGrid = screen.landscapeGridInput.validated()
    // The button is already disabled on an invalid configuration; this check is the safety net.
    if (portraitGrid == null || landscapeGrid == null) {
      showToast(StreamURLsToastMessage.INVALID_GRID)
      return
    }
    val streamCount = draft.value.streamUrls.size

    viewModelScope.launch {
      // Scoped to the two grids: the URL set and the orders are left untouched.
      saveGridPreferencesUseCase(
        portraitGrid = portraitGrid,
        landscapeGrid = landscapeGrid
      ).onSuccess {
        // The saved grids go back into the draft so that a later URL save does not rewrite
        // storage with the stale grids.
        draft.update { it.copy(portraitGrid = portraitGrid, landscapeGrid = landscapeGrid) }
        screenState.update { it.copy(isGridSectionDirty = false) }
        showToast(StreamURLsToastMessage.GRID_SAVED)

        // A grid smaller than the stream count is valid — it only warrants a non-blocking warning.
        if (!portraitGrid.showsAllStreams(streamCount) ||
          !landscapeGrid.showsAllStreams(streamCount)
        ) {
          showToast(StreamURLsToastMessage.GRID_HIDES_STREAMS)
        }
      }.onFailure { e ->
        Log.e(DEBUG_TAG, "Failed to save grid preferences", e)
        showToast(StreamURLsToastMessage.SAVE_GRID_ERROR)
      }
    }
  }

  /** Triggered by both the TopBar back button and the system back press. */
  fun onBackRequested() {
    if (screenState.value.isDirty) {
      showDialog(StreamURLsDialog.ConfirmDiscard)
      return
    }
    navigateBack()
  }

  fun onDiscardChangesConfirmed() {
    dismissDialog()
    navigateBack()
  }

  fun onDialogDismissed() {
    dismissDialog()
  }

  private fun navigateBack() {
    viewModelScope.launch {
      _navigateBackEvent.send(Unit)
    }
  }

  private fun closeAddUrlForm() {
    screenState.update { it.copy(isAddFormVisible = false, portInput = "", streamNameInput = "") }
  }

  private fun showDialog(dialog: StreamURLsDialog) {
    screenState.update { it.copy(dialog = dialog) }
  }

  private fun dismissDialog() {
    screenState.update { it.copy(dialog = null) }
  }

  /**
   * Single write point for section A: it changes the draft and marks the section as pending.
   * There is no mirror to update — `uiState.streamUrls` is derived from [draft].
   */
  private fun updateStreamUrls(transform: (StreamPreferences) -> StreamPreferences) {
    draft.update(transform)
    screenState.update { it.copy(isUrlsSectionDirty = true) }
  }

  /**
   * Single write point for the grid fields. Section B's draft lives in [screenState] rather than
   * in [draft] because the fields are free text while the user types — they only become
   * [com.gdisys.cameras.core.storage.domain.model.GridPreferences] on save.
   */
  private fun updateGridInput(
    orientation: StreamOrientation,
    transform: (GridInput) -> GridInput
  ) {
    screenState.update { screen ->
      when (orientation) {
        StreamOrientation.PORTRAIT ->
          screen.copy(portraitGridInput = transform(screen.portraitGridInput))

        StreamOrientation.LANDSCAPE ->
          screen.copy(landscapeGridInput = transform(screen.landscapeGridInput))
      }.copy(isGridSectionDirty = true)
    }
  }
}

/**
 * State the screen owns and that does not belong to the persistable draft: form fields, the open
 * dialog and the pending-save flags.
 *
 * It exists apart from [StreamURLsUiState] precisely so that the URL list has a single owner
 * ([StreamURLsViewModel.draft]); the state exposed to the UI is the combination of the two.
 */
private data class StreamURLsScreenState(
  val isLoading: Boolean = true,
  val isAddFormVisible: Boolean = false,
  val portInput: String = "",
  val streamNameInput: String = "",
  val isUrlsSectionDirty: Boolean = false,
  val portraitGridInput: GridInput = GridInput(),
  val landscapeGridInput: GridInput = GridInput(),
  val isGridSectionDirty: Boolean = false,
  val dialog: StreamURLsDialog? = null
) {
  /**
   * Same rule as [StreamURLsUiState.isDirty], evaluated at the source: both pending flags are
   * born here, and reading them here does not depend on when `combine` propagates.
   */
  val isDirty: Boolean
    get() = isUrlsSectionDirty || isGridSectionDirty

  fun toUiState(preferences: StreamPreferences): StreamURLsUiState = StreamURLsUiState(
    isLoading = isLoading,
    streamUrls = preferences.streamUrls,
    isAddFormVisible = isAddFormVisible,
    portInput = portInput,
    streamNameInput = streamNameInput,
    isUrlsSectionDirty = isUrlsSectionDirty,
    portraitGridInput = portraitGridInput,
    landscapeGridInput = landscapeGridInput,
    isGridSectionDirty = isGridSectionDirty,
    dialog = dialog
  )
}
