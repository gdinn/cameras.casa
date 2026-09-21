package com.gdisys.cameras.core.webrtc

import org.webrtc.VideoSink

/**
 * Contract for the WHEP client.
 *
 * It sits outside `domain` on purpose: the sink type comes from the WebRTC SDK, so this contract
 * owns that dependency openly instead of pretending to be pure domain.
 */
interface WhepClient {
  /**
   * Negotiates one video-receiving session over WHEP (WebRTC-HTTP Egress Protocol).
   */
  suspend fun connect(streamUrl: String, videoSink: VideoSink)

  /**
   * Closes the current connection.
   */
  fun close()
}
