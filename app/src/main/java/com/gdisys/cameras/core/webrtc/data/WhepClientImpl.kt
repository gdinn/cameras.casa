package com.gdisys.cameras.core.webrtc.data

import com.gdisys.cameras.core.webrtc.data.extensions.createOfferSuspend
import com.gdisys.cameras.core.webrtc.data.extensions.setLocalDescriptionSuspend
import com.gdisys.cameras.core.webrtc.data.extensions.setRemoteDescriptionSuspend
import com.gdisys.cameras.core.webrtc.data.remote.WhepRemoteDataSource
import com.gdisys.cameras.core.webrtc.WhepClient
import kotlinx.coroutines.CompletableDeferred
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import javax.inject.Inject

class WhepClientImpl @Inject constructor(
  private val factory: PeerConnectionFactory,
  private val remoteDataSource: WhepRemoteDataSource
) : WhepClient {

  private var peerConnection: PeerConnection? = null

  override suspend fun connect(streamUrl: String, videoSink: VideoSink) {
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
    pc.setLocalDescriptionSuspend(offer)
    iceGatheringComplete.await()

    val localSdp = requireNotNull(pc.localDescription) { "SDP local ausente após o ICE gathering" }

    // Chamada de rede agora é abstraída através do DataSource
    val answerSdp = remoteDataSource.postOffer(streamUrl, localSdp.description)

    pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
  }

  override fun close() {
    peerConnection?.close()
    peerConnection = null
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