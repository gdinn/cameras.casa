package com.gdisys.cameras.core.webrtc

import org.webrtc.VideoSink

/**
 * Contract for opening and closing the video connections a screen displays.
 *
 * It exists so the presentation layer depends on an abstraction instead of a concrete class in
 * `webrtc/data`, the same way it already depends on `VpnRepository` rather than on the tunnel
 * implementation. Swapping the transport (WHEP for RTSP, HLS, ...) then stops at this boundary.
 *
 * Like [WhepClient], it deliberately sits outside a `domain` package: [VideoSink] is a WebRTC SDK
 * type, and the sink is the renderer the UI itself creates. Wrapping it in a project type would be
 * unwrapped again in the same frame, so the contract owns the dependency instead of pretending to
 * be pure domain.
 */
interface StreamConnectionRepository {

  /**
   * Opens a connection for [streamUrl] and feeds its frames to [videoSink].
   *
   * Returns immediately; the handshake runs in the background and reports through [onError].
   * Connecting a URL that is already connected replaces the previous connection.
   */
  fun connect(streamUrl: String, videoSink: VideoSink, onError: (Throwable) -> Unit = {})

  /** Closes the connection of [streamUrl], if any. Unknown URLs are a no-op. */
  fun disconnect(streamUrl: String)

  /**
   * Closes every open connection and releases the implementation's resources.
   *
   * Terminal: the instance is not reusable afterwards, so it is called when the owning screen is
   * destroyed.
   */
  fun closeAll()
}
