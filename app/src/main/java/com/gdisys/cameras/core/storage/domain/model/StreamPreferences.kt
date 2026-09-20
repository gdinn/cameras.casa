package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.Serializable

/**
 * Preferências de exibição dos streams.
 *
 * [streamUrls] é a fonte de verdade sobre *quais* URLs existem; [portraitOrder] e [landscapeOrder]
 * dizem apenas *em que ordem* elas aparecem em cada orientação e devem ser permutações de
 * [streamUrls]. A leitura reconcilia as duas ordens (ver `StreamPreferences.reconciled`), de modo
 * que uma divergência nunca vira bug silencioso.
 */
@Serializable
data class StreamPreferences(
  val streamUrls: List<String> = emptyList(),
  val portraitOrder: List<String> = emptyList(),
  val landscapeOrder: List<String> = emptyList(),
  val portraitGrid: GridPreferences = GridPreferences(columns = 1, rows = 1, dynamicRows = true),
  val landscapeGrid: GridPreferences = GridPreferences(columns = 2, rows = 1, dynamicRows = true)
)

/**
 * Configuração da grade de uma orientação.
 *
 * @property columns número de colunas exibidas
 * @property rows número de linhas exibidas; ignorado quando [dynamicRows] é `true`
 * @property dynamicRows `true` = a grade cresce com scroll; `false` = linhas fixas com paginação
 */
@Serializable
data class GridPreferences(
  val columns: Int,
  val rows: Int,
  val dynamicRows: Boolean
)

/** Orientação da tela, usada para escolher a ordem e a grade correspondentes. */
enum class StreamOrientation {
  PORTRAIT,
  LANDSCAPE
}
