package com.gdisys.cameras.testing

import android.os.Handler
import android.os.Looper
import com.gdisys.cameras.core.webrtc.StreamConnectionRepository
import com.gdisys.cameras.core.webrtc.di.WhepConnectionModule
import dagger.Binds
import dagger.Module
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.testing.TestInstallIn
import org.webrtc.VideoSink
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/** What the app asked of the stream connections, in order. */
sealed interface StreamEvent {
  data class Connect(val url: String) : StreamEvent
  data class Disconnect(val url: String) : StreamEvent
  data object CloseAll : StreamEvent
}

/**
 * Records the stream connections of every screen, and decides which ones fail. Tests inject it:
 * being `@Singleton`, it is shared by all the [FakeStreamConnectionRepository] instances, one per
 * ViewModel, the way the real repository is.
 */
@Singleton
class FakeStreamConnections @Inject constructor() {

  /** URLs whose connection fails. A URL added later fails from its next `connect` on (a retry). */
  val failingUrls: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

  private val lock = Any()
  private val openUrls = mutableSetOf<String>()
  private val recordedEvents = mutableListOf<StreamEvent>()

  /** The URLs with an open connection right now, across every screen. */
  val connected: Set<String> get() = synchronized(lock) { openUrls.toSet() }

  /** Every `connect`, `disconnect` and `closeAll`, in the order they were called. */
  val events: List<StreamEvent> get() = synchronized(lock) { recordedEvents.toList() }

  internal fun record(event: StreamEvent, opened: String? = null, closed: Collection<String> = emptyList()) {
    synchronized(lock) {
      recordedEvents += event
      openUrls -= closed.toSet()
      opened?.let { openUrls += it }
    }
  }
}

/**
 * Connections without WebRTC. `connect` opens the URL at once, unless it is in
 * [FakeStreamConnections.failingUrls]: then it stays closed and `onError` is posted to the main
 * thread, as the real handshake reports it. The `VideoSink` never gets a frame.
 *
 * Unscoped in `ViewModelComponent` like the real one, so `closeAll` closes only this screen's URLs.
 */
class FakeStreamConnectionRepository @Inject constructor(
  private val recorder: FakeStreamConnections
) : StreamConnectionRepository {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val ownUrls = Collections.synchronizedSet(mutableSetOf<String>())

  override fun connect(streamUrl: String, videoSink: VideoSink, onError: (Throwable) -> Unit) {
    if (streamUrl in recorder.failingUrls) {
      // Connecting replaces the previous connection, even when the new one fails.
      ownUrls -= streamUrl
      recorder.record(StreamEvent.Connect(streamUrl), closed = listOf(streamUrl))
      mainHandler.post { onError(IllegalStateException("Fake connection to $streamUrl failed")) }
    } else {
      ownUrls += streamUrl
      recorder.record(StreamEvent.Connect(streamUrl), opened = streamUrl)
    }
  }

  override fun disconnect(streamUrl: String) {
    ownUrls -= streamUrl
    recorder.record(StreamEvent.Disconnect(streamUrl), closed = listOf(streamUrl))
  }

  override fun closeAll() {
    val closed = synchronized(ownUrls) { ownUrls.toList().also { ownUrls.clear() } }
    recorder.record(StreamEvent.CloseAll, closed = closed)
  }
}

/**
 * Replaces the WHEP connections in every `@HiltAndroidTest`. Nothing else asks for `WhepClient`,
 * so the `PeerConnectionFactory` is never created; the real `EglBase` stays, for the renderers.
 */
@Module
@TestInstallIn(components = [ViewModelComponent::class], replaces = [WhepConnectionModule::class])
abstract class TestStreamConnectionModule {

  @Binds
  abstract fun bindStreamConnectionRepository(
    fake: FakeStreamConnectionRepository
  ): StreamConnectionRepository
}
