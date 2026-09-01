package com.gdisys.cameras.core.webrtc.data

import android.util.Log
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.webrtc.WhepClient
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
 * Orquestra o ciclo de vida das conexões WHEP (abrir/fechar clients, cancelar jobs por URL).
 * Vive em `core/webrtc` porque, assim como [WhepClient], lida com tipos e recursos do SDK
 * WebRTC; mantém essa orquestração fora da camada de apresentação e sincroniza o acesso aos
 * mapas de jobs/clients, que antes eram mutados a partir de coroutines sem nenhuma proteção.
 */
class WhepConnectionManager @Inject constructor(
  private val whepClientProvider: Provider<WhepClient>
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val lock = Any()
  private val connectionJobs = mutableMapOf<String, Job>()
  private val clients = mutableMapOf<String, WhepClient>()

  fun connect(streamUrl: String, videoSink: VideoSink) {
    val job = scope.launch {
      try {
        val whepClient = whepClientProvider.get()
        whepClient.connect(streamUrl, videoSink)
        synchronized(lock) { clients[streamUrl] = whepClient }
      } catch (e: Exception) {
        Log.d(DEBUG_TAG, e.message.toString())
      }
    }
    synchronized(lock) { connectionJobs[streamUrl] = job }
  }

  fun disconnect(streamUrl: String) {
    val (job, client) = synchronized(lock) {
      connectionJobs.remove(streamUrl) to clients.remove(streamUrl)
    }
    job?.cancel()
    client?.close()
  }

  fun closeAll() {
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
