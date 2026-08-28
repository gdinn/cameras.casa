package com.gdisys.cameras.core.webrtc.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val HTTP_TIMEOUT_MS = 10_000
private const val HTTP_CREATED = 201

/**
 * Negocia uma sessão de recepção de vídeo via WHEP (WebRTC-HTTP Egress Protocol) com um
 * servidor de mídia e entrega os frames recebidos ao [VideoSink] informado em [connect].
 *
 * Uma instância cuida de uma única conexão por vez; chame [close] antes de reutilizá-la
 * para um novo [connect].
 */
class WhepPeerConnectionClient(
  private val factory: PeerConnectionFactory
) {
  private var peerConnection: PeerConnection? = null

  suspend fun connect(streamUrl: String, videoSink: VideoSink) {
    val iceGatheringComplete = CompletableDeferred<Unit>()

    val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
      sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
    }

    val pc = requireNotNull(
      factory.createPeerConnection(
        rtcConfig,
        TrackObserver(videoSink) { iceGatheringComplete.complete(Unit) }
      )
    ) { "Não foi possível criar a PeerConnection" }
    peerConnection = pc

    pc.addTransceiver(
      MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
      RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
    )

    val offer = pc.createOfferSuspend()
    pc.setLocalDescriptionSuspend(offer) // dispara o início do ICE gathering
    iceGatheringComplete.await()

    val localSdp = requireNotNull(pc.localDescription) { "SDP local ausente após o ICE gathering" }
    val answerSdp = postOffer(streamUrl, localSdp.description)
    pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
  }

  fun close() {
    peerConnection?.close()
    peerConnection = null
  }

  private suspend fun postOffer(streamUrl: String, offerSdp: String): String =
    withContext(Dispatchers.IO) {
      val connection = URL("$streamUrl/whep").openConnection() as HttpURLConnection
      try {
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/sdp")
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.doOutput = true
        connection.outputStream.use { it.write(offerSdp.toByteArray()) }

        check(connection.responseCode == HTTP_CREATED) {
          "Servidor WHEP retornou ${connection.responseCode} para $streamUrl"
        }
        connection.inputStream.bufferedReader().readText()
      } finally {
        connection.disconnect()
      }
    }

  private class TrackObserver(
    private val videoSink: VideoSink,
    private val onIceGatheringComplete: () -> Unit
  ) : PeerConnection.Observer {
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
      if (state == PeerConnection.IceGatheringState.COMPLETE) onIceGatheringComplete()
    }

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
      (receiver?.track() as? VideoTrack)?.addSink(videoSink)
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceCandidate(candidate: IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onDataChannel(channel: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
  }
}

private suspend fun PeerConnection.createOfferSuspend(): SessionDescription =
  suspendCancellableCoroutine { cont ->
    createOffer(object : SdpObserver {
      override fun onCreateSuccess(sdp: SessionDescription?) {
        if (sdp != null) cont.resume(sdp)
        else cont.resumeWithException(IllegalStateException("Offer criado sem SDP"))
      }

      override fun onSetSuccess() {}
      override fun onCreateFailure(error: String?) {
        cont.resumeWithException(IllegalStateException("Falha ao criar offer: $error"))
      }

      override fun onSetFailure(error: String?) {}
    }, MediaConstraints())
  }

private suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription): Unit =
  suspendCancellableCoroutine { cont ->
    setLocalDescription(object : SdpObserver {
      override fun onCreateSuccess(sdp: SessionDescription?) {}
      override fun onSetSuccess() {
        cont.resume(Unit)
      }

      override fun onCreateFailure(error: String?) {}
      override fun onSetFailure(error: String?) {
        cont.resumeWithException(IllegalStateException("Falha ao definir SDP local: $error"))
      }
    }, sdp)
  }

private suspend fun PeerConnection.setRemoteDescriptionSuspend(sdp: SessionDescription): Unit =
  suspendCancellableCoroutine { cont ->
    setRemoteDescription(object : SdpObserver {
      override fun onCreateSuccess(sdp: SessionDescription?) {}
      override fun onSetSuccess() {
        cont.resume(Unit)
      }

      override fun onCreateFailure(error: String?) {}
      override fun onSetFailure(error: String?) {
        cont.resumeWithException(IllegalStateException("Falha ao definir SDP remoto: $error"))
      }
    }, sdp)
  }
