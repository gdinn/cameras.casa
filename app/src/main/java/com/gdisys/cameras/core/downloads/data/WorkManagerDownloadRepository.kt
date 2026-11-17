package com.gdisys.cameras.core.downloads.data

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gdisys.cameras.core.coroutines.ApplicationScope
import com.gdisys.cameras.core.coroutines.IoDispatcher
import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.storage.domain.DownloadRecordsRepository
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How long a resume waits for an interrupted download's old work to end. The worker returns right
 * after updating its record, so this only covers a slow WorkManager; past it, the request goes on
 * and is refused as a duplicate if the old work is still there.
 */
internal const val OLD_WORK_TIMEOUT_MS = 5_000L

/**
 * [DownloadRepository] over WorkManager, one unique work per recording ([downloadWorkName]).
 *
 * **The tunnel holder.** Before enqueueing, it takes a [TunnelHolder.Download] keyed by the work id
 * with `holdIfUp`, so the download starts only while the tunnel is up and keeps it up while it
 * runs. A watcher in the application scope releases the holder once the work ends, however it ends.
 * Holders live in memory: the worker of a process that died finds none and stops (see
 * [RecordingDownloadWorker]).
 *
 * **Records.** Each download has a record (`DownloadRecordsRepository`) from the request until it
 * ends well, so the next launch can report what a dead process or a closed app left unfinished
 * ([interrupted]) and delete its partial file ([resume], [discard]).
 */
@Singleton
class WorkManagerDownloadRepository @Inject constructor(
  private val workManager: WorkManager,
  private val tunnelCoordinator: TunnelCoordinator,
  private val records: DownloadRecordsRepository,
  private val outputs: DownloadOutputs,
  @param:ApplicationScope private val scope: CoroutineScope,
  @param:IoDispatcher private val io: CoroutineDispatcher
) : DownloadRepository {

  /** Two quick requests for the same recording must not both get past the "already running" check. */
  private val mutex = Mutex()

  override suspend fun download(recording: Recording): DownloadRequestResult = mutex.withLock {
    val name = downloadWorkName(recording)
    if (workManager.getWorkInfosForUniqueWorkFlow(name).first().any { !it.state.isFinished }) {
      return DownloadRequestResult.ALREADY_RUNNING
    }
    val request = OneTimeWorkRequestBuilder<RecordingDownloadWorker>()
      .setInputData(downloadInput(recording))
      .addTag(DOWNLOAD_WORK_TAG)
      .build()
    val holder = TunnelHolder.Download(request.id.toString())
    if (!tunnelCoordinator.holdIfUp(holder)) return DownloadRequestResult.TUNNEL_DOWN

    // Recorded before it can start, so the worker always finds its record.
    records.update { it + newDownloadRecord(request.id.toString(), recording) }
    workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    scope.launch {
      // firstOrNull: should the flow ever complete without a final state, the holder still goes.
      workManager.getWorkInfoByIdFlow(request.id).firstOrNull { it == null || it.state.isFinished }
      tunnelCoordinator.release(holder)
    }
    DownloadRequestResult.STARTED
  }

  override fun observe(recording: Recording): Flow<DownloadStatus?> =
    workManager.getWorkInfosForUniqueWorkFlow(downloadWorkName(recording))
      .map { infos -> infos.lastOrNull()?.toDownloadStatus() }

  override fun interrupted(): Flow<List<InterruptedDownload>> = records.records.map { list ->
    list.toInterruptedDownloads { workId -> tunnelCoordinator.holds(TunnelHolder.Download(workId)) }
  }

  /**
   * Each download is requested again once its old work has ended. The record is marked failed or
   * interrupted just before the worker returns, so the offer can be answered while WorkManager
   * still has the old work running: requested right then, it would be refused as a duplicate. Only
   * a download that started forgets its old record (and partial file); the others stay offered.
   */
  override suspend fun resume(downloads: List<InterruptedDownload>): List<DownloadRequestResult> =
    downloads.map { interrupted ->
      awaitEnded(interrupted.id)
      download(interrupted.recording).also { result ->
        if (result == DownloadRequestResult.STARTED) discard(listOf(interrupted))
      }
    }

  /** Waits, up to [OLD_WORK_TIMEOUT_MS], for the work [workId] to be finished or gone. */
  private suspend fun awaitEnded(workId: String) {
    val id = runCatching { UUID.fromString(workId) }.getOrNull() ?: return
    withTimeoutOrNull(OLD_WORK_TIMEOUT_MS) {
      workManager.getWorkInfoByIdFlow(id).firstOrNull { it == null || it.state.isFinished }
    }
  }

  override suspend fun discard(downloads: List<InterruptedDownload>) {
    val ids = downloads.mapTo(mutableSetOf()) { it.id }
    val leftovers = records.records.first().filter { it.workId in ids }.mapNotNull { it.partialFile }
    withContext(io) { leftovers.forEach(outputs::deletePartial) }
    records.update { list -> list.filterNot { it.workId in ids } }
  }

  override suspend fun interruptAll() {
    // Marked first: the cancelled workers then keep their records, to be reported (afterStop).
    records.update { it.allRunningInterrupted() }
    workManager.cancelAllWorkByTag(DOWNLOAD_WORK_TAG)
  }
}
