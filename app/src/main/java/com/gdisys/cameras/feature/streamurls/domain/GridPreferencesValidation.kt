package com.gdisys.cameras.feature.streamurls.domain

import com.gdisys.cameras.core.storage.domain.model.GridPreferences

/** Faixa aceita para colunas e linhas da grade, nas duas orientações. */
const val MIN_GRID_DIMENSION = 1
const val MAX_GRID_DIMENSION = 6

/**
 * Linhas gravadas quando o modo dinâmico está ativo e o campo de linhas não tem um valor válido.
 *
 * No modo dinâmico o número de linhas é ignorado na exibição, mas o modelo persiste um inteiro;
 * gravar um valor dentro da faixa mantém a preferência coerente caso o usuário desligue o modo
 * dinâmico mais tarde.
 */
private const val FALLBACK_ROWS = MIN_GRID_DIMENSION

/**
 * Valida os campos da grade de uma orientação e devolve as preferências correspondentes, ou `null`
 * quando a configuração é inválida — que é o que desabilita o botão de salvar da seção.
 *
 * Colunas são sempre obrigatórias. Linhas só são exigidas fora do modo dinâmico: com
 * [dynamicRows] ligado o campo é ignorado, então um valor vazio ou inválido ali não bloqueia o
 * salvamento.
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
 * `false` quando a grade é menor do que a quantidade de streams cadastrados — configuração válida,
 * mas que merece um aviso, já que parte dos streams fica de fora da tela.
 *
 * No modo dinâmico a grade cresce com scroll, então ela sempre exibe todos.
 */
fun GridPreferences.showsAllStreams(streamCount: Int): Boolean =
  dynamicRows || columns * rows >= streamCount
