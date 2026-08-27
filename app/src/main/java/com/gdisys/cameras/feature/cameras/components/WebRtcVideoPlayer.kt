@file:OptIn(ExperimentalMaterial3Api::class)

package com.gdisys.cameras.feature.cameras.components

import android.view.ViewGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.EglBase
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
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun WebRtcVideoPlayer(
  streamUrl: String,
  factory: PeerConnectionFactory,
  eglBase: EglBase,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val renderer = remember {
    SurfaceViewRenderer(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      init(eglBase.eglBaseContext, null)
      setMirror(false)
      setEnableHardwareScaler(true)
    }
  }

  DisposableEffect(streamUrl) {
    val whepUrl = "$streamUrl/whep"

    val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
      sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
    }

    var pc: PeerConnection? = null

    pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
      override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
        if (state != PeerConnection.IceGatheringState.COMPLETE) return
        val localSdp = pc?.localDescription ?: return
        scope.launch(Dispatchers.IO) {
          runCatching {
            val conn = URL(whepUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/sdp")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.outputStream.use { it.write(localSdp.description.toByteArray()) }
            if (conn.responseCode == 201) {
              val answerSdp = conn.inputStream.bufferedReader().readText()
              pc?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
              }, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
            }
          }
        }
      }

      override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        (receiver?.track() as? VideoTrack)?.addSink(renderer)
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
    })

    pc?.addTransceiver(
      MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
      RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
    )

    pc?.createOffer(object : SdpObserver {
      override fun onCreateSuccess(sdp: SessionDescription?) {
        sdp ?: return
        pc.setLocalDescription(object : SdpObserver {
          override fun onCreateSuccess(p0: SessionDescription?) {}
          override fun onSetSuccess() {} // ICE gathering inicia aqui
          override fun onCreateFailure(p0: String?) {}
          override fun onSetFailure(p0: String?) {}
        }, sdp)
      }
      override fun onSetSuccess() {}
      override fun onCreateFailure(error: String?) {}
      override fun onSetFailure(error: String?) {}
    }, MediaConstraints())

    onDispose { pc?.close() }
  }

  DisposableEffect(Unit) {
    onDispose {
      renderer.release()
    }
  }

  AndroidView(factory = { renderer }, modifier = modifier)
}


