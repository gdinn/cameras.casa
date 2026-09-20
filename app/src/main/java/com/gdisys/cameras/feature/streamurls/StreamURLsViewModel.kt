package com.gdisys.cameras.feature.streamurls

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveGridPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveStreamUrlsUseCase
import com.gdisys.cameras.feature.streamurls.domain.StreamUrlValidationResult
import com.gdisys.cameras.feature.streamurls.domain.showsAllStreams
import com.gdisys.cameras.feature.streamurls.domain.validateStreamUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreamURLsViewModel @Inject constructor(
  getStreamPreferencesUseCase: GetStreamPreferencesUseCase,
  private val saveStreamUrlsUseCase: SaveStreamUrlsUseCase,
  private val saveGridPreferencesUseCase: SaveGridPreferencesUseCase
) : ToastEventViewModel() {

  private val _uiState = MutableStateFlow(StreamURLsUiState())
  val uiState: StateFlow<StreamURLsUiState> = _uiState.asStateFlow()

  /**
   * Rascunho completo das preferências, semeado uma única vez a partir do storage. Carrega mais
   * do que a UI exibe (as duas ordens e as grades) justamente para que o salvar consiga reescrever
   * as ordens junto do conjunto canônico, sem perder o que a tela ainda não edita.
   */
  private var editedPreferences = StreamPreferences()

  private val _navigateBackEvent = Channel<Unit>()
  val navigateBackEvent: Flow<Unit> = _navigateBackEvent.receiveAsFlow()

  init {
    viewModelScope.launch {
      // A partir daqui a tela é dona do rascunho: mudanças posteriores no storage não o
      // sobrescrevem, ou o usuário perderia o que está editando.
      val storedPreferences = getStreamPreferencesUseCase().first()
      editedPreferences = storedPreferences
      _uiState.update {
        it.copy(
          isLoading = false,
          streamUrls = storedPreferences.streamUrls,
          portraitGridInput = storedPreferences.portraitGrid.toInput(),
          landscapeGridInput = storedPreferences.landscapeGrid.toInput()
        )
      }
    }
  }

  fun onShowAddUrlForm() {
    if (_uiState.value.isAddFormVisible) return
    _uiState.update { it.copy(isAddFormVisible = true, portInput = "", streamNameInput = "") }
  }

  fun onPortChanged(port: String) {
    _uiState.update { it.copy(portInput = port) }
  }

  fun onStreamNameChanged(streamName: String) {
    _uiState.update { it.copy(streamNameInput = streamName) }
  }

  fun onCancelAddUrlForm() {
    closeAddUrlForm()
  }

  fun onAddUrlConfirmed() {
    val state = _uiState.value
    val result = validateStreamUrl(
      port = state.portInput,
      streamName = state.streamNameInput,
      existingUrls = editedPreferences.streamUrls
    )

    when (result) {
      is StreamUrlValidationResult.Valid -> {
        // A URL entra no conjunto canônico e no fim das duas ordens.
        updateEditedPreferences { preferences ->
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
    updateEditedPreferences { preferences ->
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
    // Substituição só em memória: as duas ordens reiniciam na sequência do padrão.
    updateEditedPreferences { preferences ->
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
    val preferences = editedPreferences
    viewModelScope.launch {
      saveStreamUrlsUseCase(
        streamUrls = preferences.streamUrls,
        portraitOrder = preferences.portraitOrder,
        landscapeOrder = preferences.landscapeOrder
      ).onSuccess {
        _uiState.update { it.copy(isUrlsSectionDirty = false) }
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
    val state = _uiState.value
    val portraitGrid = state.portraitGridInput.validated()
    val landscapeGrid = state.landscapeGridInput.validated()
    // O botão já fica desabilitado com configuração inválida; a checagem aqui é a rede de proteção.
    if (portraitGrid == null || landscapeGrid == null) {
      showToast(StreamURLsToastMessage.INVALID_GRID)
      return
    }

    viewModelScope.launch {
      // Escopo restrito às duas grades: o conjunto de URLs e as ordens não são tocados.
      saveGridPreferencesUseCase(
        portraitGrid = portraitGrid,
        landscapeGrid = landscapeGrid
      ).onSuccess {
        editedPreferences = editedPreferences.copy(
          portraitGrid = portraitGrid,
          landscapeGrid = landscapeGrid
        )
        _uiState.update { it.copy(isGridSectionDirty = false) }
        showToast(StreamURLsToastMessage.GRID_SAVED)

        // Grade menor que o número de streams é válido — só merece um aviso não bloqueante.
        val streamCount = state.streamUrls.size
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

  /** Acionado tanto pelo botão de voltar da TopBar quanto pelo back do sistema. */
  fun onBackRequested() {
    if (_uiState.value.isDirty) {
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
    _uiState.update { it.copy(isAddFormVisible = false, portInput = "", streamNameInput = "") }
  }

  private fun showDialog(dialog: StreamURLsDialog) {
    _uiState.update { it.copy(dialog = dialog) }
  }

  private fun dismissDialog() {
    _uiState.update { it.copy(dialog = null) }
  }

  /**
   * Ponto único de escrita no rascunho: mantém o espelho exibido pela UI em sincronia com
   * [editedPreferences] e marca a seção de URLs como pendente de salvamento.
   */
  private fun updateEditedPreferences(transform: (StreamPreferences) -> StreamPreferences) {
    editedPreferences = transform(editedPreferences)
    _uiState.update {
      it.copy(streamUrls = editedPreferences.streamUrls, isUrlsSectionDirty = true)
    }
  }

  /**
   * Ponto único de escrita nos campos da grade. O rascunho da seção B vive no próprio `UiState`
   * (e não em [editedPreferences]) porque os campos são texto livre enquanto o usuário digita —
   * só viram [com.gdisys.cameras.core.storage.domain.model.GridPreferences] no salvar.
   */
  private fun updateGridInput(
    orientation: StreamOrientation,
    transform: (GridInput) -> GridInput
  ) {
    _uiState.update { state ->
      when (orientation) {
        StreamOrientation.PORTRAIT ->
          state.copy(portraitGridInput = transform(state.portraitGridInput))

        StreamOrientation.LANDSCAPE ->
          state.copy(landscapeGridInput = transform(state.landscapeGridInput))
      }.copy(isGridSectionDirty = true)
    }
  }
}
