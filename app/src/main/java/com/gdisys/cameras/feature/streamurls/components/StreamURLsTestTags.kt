package com.gdisys.cameras.feature.streamurls.components

import com.gdisys.cameras.core.storage.domain.model.StreamOrientation

/**
 * Semantic tags shared by [StreamURLsScreen], its components and its instrumented tests.
 *
 * Tests find nodes by tag rather than by text because the screen is translated, and because some
 * labels repeat on screen (every confirmation dialog has a "Confirm" button).
 */
object StreamURLsTestTags {
  const val LOADING = "stream_urls_loading"
  const val LIST = "stream_urls_list"
  const val BACK_BUTTON = "stream_urls_back_button"
  const val ADD_URL_BUTTON = "stream_urls_add_url_button"
  const val NO_URLS_MESSAGE = "stream_urls_no_urls_message"
  const val ADD_URL_FORM = "stream_urls_add_url_form"
  const val PORT_INPUT = "stream_urls_port_input"
  const val STREAM_NAME_INPUT = "stream_urls_stream_name_input"
  const val CONFIRM_ADD_URL_BUTTON = "stream_urls_confirm_add_url_button"
  const val CANCEL_ADD_URL_BUTTON = "stream_urls_cancel_add_url_button"
  const val LOAD_DEFAULTS_BUTTON = "stream_urls_load_defaults_button"
  const val SAVE_URLS_BUTTON = "stream_urls_save_urls_button"
  const val SAVE_GRID_BUTTON = "stream_urls_save_grid_button"
  const val DIALOG_CONFIRM_BUTTON = "stream_urls_dialog_confirm_button"
  const val DIALOG_DISMISS_BUTTON = "stream_urls_dialog_dismiss_button"

  // Per-item tags. The URL is already the LazyColumn key, so it is unique on screen.
  fun urlRow(url: String) = "stream_urls_row:$url"
  fun removeUrlButton(url: String) = "stream_urls_remove:$url"

  fun gridColumnsInput(orientation: StreamOrientation) = "stream_urls_grid_columns:$orientation"
  fun gridRowsInput(orientation: StreamOrientation) = "stream_urls_grid_rows:$orientation"
  fun gridDynamicRowsCheckbox(orientation: StreamOrientation) =
    "stream_urls_grid_dynamic:$orientation"
}
