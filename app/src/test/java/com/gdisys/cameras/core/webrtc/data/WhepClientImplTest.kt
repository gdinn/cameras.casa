package com.gdisys.cameras.core.webrtc.data

import com.gdisys.cameras.core.webrtc.data.remote.WhepRemoteDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.VideoSink

/**
 * Covers only the release of the PeerConnection. The negotiation itself runs on the native WebRTC
 * stack and is exercised on a device.
 */
class WhepClientImplTest {

  private val peerConnection = mockk<PeerConnection>(relaxed = true)
  private val factory = mockk<PeerConnectionFactory> {
    every {
      createPeerConnection(any<PeerConnection.RTCConfiguration>(), any<PeerConnection.Observer>())
    } returns peerConnection
  }
  private val client = WhepClientImpl(factory, mockk<WhepRemoteDataSource>())

  /**
   * Creates the PeerConnection and fails right after it, the way a negotiation that breaks halfway
   * leaves the client holding a PeerConnection.
   */
  private fun connectAndFailAfterCreatingThePeerConnection() {
    every {
      peerConnection.addTransceiver(
        any<MediaStreamTrack.MediaType>(),
        any<RtpTransceiver.RtpTransceiverInit>()
      )
    } throws IllegalStateException("boom")

    assertThrows(IllegalStateException::class.java) {
      runBlocking { client.connect("stream-url", mockk<VideoSink>()) }
    }
  }

  @Test
  fun `close disposes the PeerConnection instead of only closing it`() {
    connectAndFailAfterCreatingThePeerConnection()

    client.close()

    verify(exactly = 1) { peerConnection.dispose() }
  }

  @Test
  fun `closing twice disposes the PeerConnection only once`() {
    connectAndFailAfterCreatingThePeerConnection()

    client.close()
    client.close()

    verify(exactly = 1) { peerConnection.dispose() }
  }

  @Test
  fun `close before connect does nothing`() {
    client.close()

    verify(exactly = 0) { peerConnection.dispose() }
  }
}
