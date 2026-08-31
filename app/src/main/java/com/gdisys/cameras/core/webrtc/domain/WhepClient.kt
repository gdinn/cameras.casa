package com.gdisys.cameras.core.webrtc.domain

import org.webrtc.VideoSink

/**
 * Contrato para o cliente WHEP.
 */
interface WhepClient {
  /**
   * Negocia uma sessão de recepção de vídeo via WHEP (WebRTC-HTTP Egress Protocol).
   */
  suspend fun connect(streamUrl: String, videoSink: VideoSink)

  /**
   * Encerra a conexão atual.
   */
  fun close()
}
