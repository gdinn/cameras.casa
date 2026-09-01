package com.gdisys.cameras.core.webrtc

import org.webrtc.VideoSink

/**
 * Contrato para o cliente WHEP.
 *
 * Fica fora de `domain` de propósito: o tipo do sink é do SDK WebRTC,
 * então este contrato já assume a dependência em vez de fingir ser domínio puro.
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
