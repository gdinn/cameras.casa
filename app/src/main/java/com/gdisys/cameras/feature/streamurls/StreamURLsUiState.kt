package com.gdisys.cameras.feature.streamurls

import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.feature.streamurls.logic.validateGridPreferences

/**
 * Estado da tela de Stream URLs.
 *
 * Toda a edição é feita em memória: [streamUrls] e os dois [GridInput] refletem o rascunho do
 * usuário, não o que está persistido. Nada chega ao storage antes do salvar.
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
  /** Somente uma URL por vez: o `+` fica travado enquanto o formulário está aberto. */
  val isAddUrlButtonEnabled: Boolean
    get() = !isAddFormVisible

  /** Salvar a grade só é possível com as duas orientações válidas. */
  val isSaveGridButtonEnabled: Boolean
    get() = portraitGridInput.validated() != null && landscapeGridInput.validated() != null

  /** União das pendências de todas as seções da tela — é o que dispara o diálogo de descarte. */
  val isDirty: Boolean
    get() = isUrlsSectionDirty || isGridSectionDirty
}

/**
 * Campos da grade de uma orientação, como texto — é o que o usuário digita, incluindo valores
 * incompletos ou inválidos. A conversão para o modelo acontece em [validated].
 */
data class GridInput(
  val columns: String = "",
  val rows: String = "",
  val dynamicRows: Boolean = true
) {
  /** [GridPreferences] correspondente, ou `null` quando a configuração ainda é inválida. */
  fun validated(): GridPreferences? = validateGridPreferences(columns, rows, dynamicRows)
}

/** Campos de edição correspondentes a uma grade persistida. */
fun GridPreferences.toInput(): GridInput = GridInput(
  columns = columns.toString(),
  rows = rows.toString(),
  dynamicRows = dynamicRows
)

/** Diálogos de confirmação da tela; apenas um pode estar visível por vez. */
sealed interface StreamURLsDialog {
  /** Salvar sobrescreve o que está persistido. */
  data object ConfirmSaveUrls : StreamURLsDialog

  /** Salvar a grade sobrescreve as duas grades persistidas. */
  data object ConfirmSaveGrid : StreamURLsDialog

  /** A configuração padrão substitui a lista atual (só em memória). */
  data object ConfirmLoadDefaults : StreamURLsDialog

  /** Voltar com alterações pendentes. */
  data object ConfirmDiscard : StreamURLsDialog
}
