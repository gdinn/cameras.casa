package com.gdisys.cameras.core.webrtc.data

import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.webrtc.WhepClient
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.webrtc.VideoSink
import javax.inject.Provider

class WhepConnectionManagerTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val whepClient = mockk<WhepClient>(relaxed = true)
  private val whepClientProvider = Provider<WhepClient> { whepClient }
  private lateinit var manager: WhepConnectionManager

  @Before
  fun setUp() {
    // WhepConnectionManager cria seu CoroutineScope com Dispatchers.Main.immediate no
    // construtor, então só pode ser instanciado depois que MainDispatcherRule já rodou.
    manager = WhepConnectionManager(whepClientProvider)
  }

  @Test
  fun `connect asks the provider for a client and connects it to the given stream`() {
    val videoSink = mockk<VideoSink>()
    coEvery { whepClient.connect(any(), any()) } just Runs

    manager.connect("stream-url", videoSink)

    coVerify(exactly = 1) { whepClient.connect("stream-url", videoSink) }
  }

  @Test
  fun `connect reports the error via onError when the client fails to connect`() {
    val videoSink = mockk<VideoSink>()
    val error = IllegalStateException("boom")
    coEvery { whepClient.connect(any(), any()) } throws error
    var reportedError: Throwable? = null

    manager.connect("stream-url", videoSink) { reportedError = it }

    assertEquals(error, reportedError)
  }

  @Test
  fun `connect does not register a client when the connection fails`() {
    val videoSink = mockk<VideoSink>()
    coEvery { whepClient.connect(any(), any()) } throws IllegalStateException("boom")

    manager.connect("stream-url", videoSink)
    manager.disconnect("stream-url")

    verify(exactly = 0) { whepClient.close() }
  }

  @Test
  fun `connect rethrows CancellationException instead of reporting it as an error`() {
    val videoSink = mockk<VideoSink>()
    coEvery { whepClient.connect(any(), any()) } throws CancellationException("cancelled")
    var reportedError: Throwable? = null

    manager.connect("stream-url", videoSink) { reportedError = it }

    assertEquals(null, reportedError)
  }

  @Test
  fun `disconnect cancels the job and closes the client registered for the url`() {
    val videoSink = mockk<VideoSink>()
    coEvery { whepClient.connect(any(), any()) } just Runs
    manager.connect("stream-url", videoSink)

    manager.disconnect("stream-url")

    verify(exactly = 1) { whepClient.close() }
  }

  @Test
  fun `disconnect on an unknown url does not throw`() {
    manager.disconnect("unknown-url")
  }

  @Test
  fun `disconnecting the same url twice only closes the client once`() {
    val videoSink = mockk<VideoSink>()
    coEvery { whepClient.connect(any(), any()) } just Runs
    manager.connect("stream-url", videoSink)

    manager.disconnect("stream-url")
    manager.disconnect("stream-url")

    verify(exactly = 1) { whepClient.close() }
  }

  @Test
  fun `closeAll closes every connected client and clears the internal state`() {
    val clientA = mockk<WhepClient>(relaxed = true)
    val clientB = mockk<WhepClient>(relaxed = true)
    val clients = ArrayDeque(listOf(clientA, clientB))
    val provider = Provider<WhepClient> { clients.removeFirst() }
    val manager = WhepConnectionManager(provider)
    coEvery { clientA.connect(any(), any()) } just Runs
    coEvery { clientB.connect(any(), any()) } just Runs
    manager.connect("a", mockk())
    manager.connect("b", mockk())

    manager.closeAll()

    verify(exactly = 1) { clientA.close() }
    verify(exactly = 1) { clientB.close() }

    // A subsequent disconnect on either url should be a no-op: the state was cleared.
    manager.disconnect("a")
    verify(exactly = 1) { clientA.close() }
  }

  @Test
  fun `closeAll with no active connections does not throw`() {
    manager.closeAll()
    assertTrue(true)
  }
}
