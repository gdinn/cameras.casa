package com.gdisys.cameras.feature.cameras.components

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * Where a stream's player is, as the user sees it. Exposed through [StreamPlaybackStateKey] so a
 * test can wait for real video without matching text or pixels.
 */
enum class StreamPlaybackState {
  /** Negotiating, or connected with no frame drawn yet: the card shows a spinner. */
  CONNECTING,

  /** The renderer drew at least one frame. A stream that drops later stays here, frozen. */
  PLAYING,

  /** The last connection attempt failed: the card shows [StreamErrorOverlay]. */
  FAILED
}

/** Semantics property carrying a player's [StreamPlaybackState]. Read only by tests. */
val StreamPlaybackStateKey = SemanticsPropertyKey<StreamPlaybackState>("StreamPlaybackState")

var SemanticsPropertyReceiver.streamPlaybackState by StreamPlaybackStateKey
