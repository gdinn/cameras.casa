package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Esquema corrente do storage de preferências de stream. */
const val STREAM_PREFERENCES_SCHEMA_VERSION = 1

/**
 * Preferências de exibição dos streams.
 *
 * [streamUrls] é a fonte de verdade sobre *quais* URLs existem; [portraitOrder] e [landscapeOrder]
 * dizem apenas *em que ordem* elas aparecem em cada orientação e devem ser permutações de
 * [streamUrls]. A leitura reconcilia as duas ordens (ver `StreamPreferences.reconciled`), de modo
 * que uma divergência nunca vira bug silencioso.
 *
 * This class doubles as the on-disk schema, so every property carries an explicit [SerialName].
 * Renaming a property in Kotlin then leaves the stored name untouched — without it, a rename makes
 * the persisted JSON unreadable and `EncryptedPreferencesSerializer` silently falls back to the
 * default value, wiping the user's cameras with no error. Adding a property stays safe as long as
 * it has a default; removing or repurposing one needs [schemaVersion] bumped and a migration.
 */
@Serializable
data class StreamPreferences(
  @SerialName("streamUrls") val streamUrls: List<String> = emptyList(),
  @SerialName("portraitOrder") val portraitOrder: List<String> = emptyList(),
  @SerialName("landscapeOrder") val landscapeOrder: List<String> = emptyList(),
  @SerialName("portraitGrid")
  val portraitGrid: GridPreferences = GridPreferences(columns = 1, rows = 1, dynamicRows = true),
  @SerialName("landscapeGrid")
  val landscapeGrid: GridPreferences = GridPreferences(columns = 2, rows = 1, dynamicRows = true),
  /**
   * Schema the stored value was written with.
   *
   * Nothing migrates on it yet — it is the hook that makes a future migration possible at all,
   * since data written today is indistinguishable from data written by any later version without
   * it. Old files lack the field and decode as [STREAM_PREFERENCES_SCHEMA_VERSION] through this
   * default, which is correct: they were written by that very schema.
   */
  @SerialName("schemaVersion") val schemaVersion: Int = STREAM_PREFERENCES_SCHEMA_VERSION
)

/**
 * Configuração da grade de uma orientação.
 *
 * Os nomes de serialização são explícitos pelo mesmo motivo de [StreamPreferences].
 *
 * @property columns número de colunas exibidas
 * @property rows número de linhas exibidas; ignorado quando [dynamicRows] é `true`
 * @property dynamicRows `true` = a grade cresce com scroll; `false` = linhas fixas com paginação
 */
@Serializable
data class GridPreferences(
  @SerialName("columns") val columns: Int,
  @SerialName("rows") val rows: Int,
  @SerialName("dynamicRows") val dynamicRows: Boolean
)

/** Orientação da tela, usada para escolher a ordem e a grade correspondentes. */
enum class StreamOrientation {
  PORTRAIT,
  LANDSCAPE
}
