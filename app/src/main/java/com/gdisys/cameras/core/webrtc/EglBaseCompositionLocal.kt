package com.gdisys.cameras.core.webrtc

import androidx.compose.runtime.staticCompositionLocalOf
import org.webrtc.EglBase

/**
 * Ambient [EglBase] for the whole composition.
 *
 * The shared EGL context is a process-wide singleton provided by Hilt, and the only place that can
 * legitimately hold it is the composition root — `MainActivity`, which injects it. Passing it down
 * by parameter instead would put a WebRTC SDK type in the signature of `NavigationRoot`, making the
 * navigation graph depend on the video stack it merely routes to.
 *
 * It lives in `core/webrtc` rather than in `feature/cameras` because no single feature owns it:
 * `core/webrtc` is already the package that deliberately handles WebRTC SDK types.
 *
 * This is for routes, not for screens. Screens take `EglBase` as an explicit parameter so they stay
 * pure and previewable.
 */
val LocalEglBase = staticCompositionLocalOf<EglBase> {
  error("LocalEglBase not provided")
}
