package com.gdisys.cameras.feature.cameras.components

/** Semantic tags shared by the Home screens and their instrumented tests. */
object HomeTestTags {
  const val EMPTY_STREAMS_RECONFIGURE_BUTTON = "home_empty_streams_reconfigure_button"
  const val STREAM_ERROR_OVERLAY = "home_stream_error_overlay"
  const val STREAM_ERROR_RETRY_BUTTON = "home_stream_error_retry_button"
  const val STREAM_CONNECTING_INDICATOR = "home_stream_connecting_indicator"

  /** The grid cell of [url], which carries the reorder accessibility actions. */
  fun streamCard(url: String) = "home_stream_card:$url"

  /** The drag handle of [url]'s cell. */
  fun dragHandle(url: String) = "home_stream_drag_handle:$url"

  /** The player of [url], which carries its [StreamPlaybackState]. */
  fun streamPlayer(url: String) = "home_stream_player:$url"
}
