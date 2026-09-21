package com.gdisys.cameras.feature.streamurls

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.ToastMessage

enum class StreamURLsToastMessage(@StringRes override val resId: Int) : ToastMessage {
  INVALID_PORT(R.string.stream_urls_route_invalid_port),
  INVALID_STREAM_NAME(R.string.stream_urls_route_invalid_stream_name),
  DUPLICATE_URL(R.string.stream_urls_route_duplicate_url),
  URLS_SAVED(R.string.stream_urls_route_urls_saved),
  SAVE_URLS_ERROR(R.string.stream_urls_route_save_error),
  INVALID_GRID(R.string.stream_urls_route_invalid_grid),
  GRID_SAVED(R.string.stream_urls_route_grid_saved),
  SAVE_GRID_ERROR(R.string.stream_urls_route_save_grid_error),
  GRID_HIDES_STREAMS(R.string.stream_urls_route_grid_hides_streams),
}
