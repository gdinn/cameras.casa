package com.gdisys.cameras.core.webrtc.data

import android.util.Log
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.webrtc.StreamConnectionRepository
import com.gdisys.cameras.core.webrtc.WhepClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.VideoSink
import javax.inject.Inject
import javax.inject.Provider

/**
 * Orchestrates the lifecycle of the WHEP connections: opening and closing clients, and cancelling
 * the job belonging to a URL. It lives in `core/webrtc` because, like [WhepClient], it handles
 * WebRTC SDK types and resources. Keeping the orchestration out of the presentation layer is half
 * the point; the other half is the lock, which guards the job and client maps — they are mutated
 * from several coroutines and used to be unsynchronized.
 *
 * WHEP implementation of [StreamConnectionRepository]: the presentation layer depends on that
 * contract, never on this class.
 */
class WhepConnectionManager @Inject constructor(
  private val whepClientProvider: Provider<WhepClient>
) : StreamConnectionRepository {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val lock = Any()
  private val connectionJobs = mutableMapOf<String, Job>()
  private val clients = mutableMapOf<String, WhepClient>()

  override fun connect(
    streamUrl: String,
    videoSink: VideoSink,
    onError: (Throwable) -> Unit
  ) {
    val job = scope.launch {
      try {
        val whepClient = whepClientProvider.get()
        whepClient.connect(streamUrl, videoSink)
        synchronized(lock) { clients[streamUrl] = whepClient }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.d(DEBUG_TAG, e.message.toString())
        onError(e)
      }
    }
    synchronized(lock) { connectionJobs[streamUrl] = job }
  }

  override fun disconnect(streamUrl: String) {
    val (job, client) = synchronized(lock) {
      connectionJobs.remove(streamUrl) to clients.remove(streamUrl)
    }
    job?.cancel()
    client?.close()
  }

  override fun closeAll() {
    val (jobs, closedClients) = synchronized(lock) {
      val jobsSnapshot = connectionJobs.values.toList()
      val clientsSnapshot = clients.values.toList()
      connectionJobs.clear()
      clients.clear()
      jobsSnapshot to clientsSnapshot
    }
    jobs.forEach { it.cancel() }
    closedClients.forEach { it.close() }
    scope.cancel()
  }
}
