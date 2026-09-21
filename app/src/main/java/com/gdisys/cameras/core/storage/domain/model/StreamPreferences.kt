package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Current schema of the stream preferences store. */
const val STREAM_PREFERENCES_SCHEMA_VERSION = 1

/**
 * Display preferences for the streams.
 *
 * [streamUrls] is the source of truth for *which* URLs exist; [portraitOrder] and [landscapeOrder]
 * only say in *what order* they appear in each orientation, and must be permutations of
 * [streamUrls]. Reads reconcile both orders (see `StreamPreferences.reconciled`), so a divergence
 * can never become a silent bug.
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
 * Grid configuration for one orientation.
 *
 * The serial names are explicit for the same reason as in [StreamPreferences].
 *
 * @property columns number of columns displayed
 * @property rows number of rows displayed; ignored when [dynamicRows] is `true`
 * @property dynamicRows `true` = the grid grows and scrolls; `false` = fixed rows with paging
 */
@Serializable
data class GridPreferences(
  @SerialName("columns") val columns: Int,
  @SerialName("rows") val rows: Int,
  @SerialName("dynamicRows") val dynamicRows: Boolean
)

/** Screen orientation, used to pick the matching order and grid. */
enum class StreamOrientation {
  PORTRAIT,
  LANDSCAPE
}
