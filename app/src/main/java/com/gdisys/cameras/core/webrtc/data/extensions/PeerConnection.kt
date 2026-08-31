package com.gdisys.cameras.core.webrtc.data.extensions

import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun PeerConnection.createOfferSuspend(): SessionDescription =
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

suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription): Unit =
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

suspend fun PeerConnection.setRemoteDescriptionSuspend(sdp: SessionDescription): Unit =
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