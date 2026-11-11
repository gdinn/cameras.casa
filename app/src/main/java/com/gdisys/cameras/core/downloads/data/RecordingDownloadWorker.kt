package com.gdisys.cameras.core.downloads.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.coroutines.IoDispatcher
import com.gdisys.cameras.core.recordings.data.RecordingFileSources
import com.gdisys.cameras.core.recordings.data.open
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Copies one recording from the share to `Download/Cameras`, as a `dataSync` foreground service.
 *
 * - **Tunnel:** the download's [TunnelHolder] was taken when it was enqueued, while the tunnel was
 *   up. No holder means this run is WorkManager's re-run after the process died: the worker fails
 *   as interrupted, without touching the network. It never raises the tunnel.
 * - **Progress:** published as work progress and on the notification, once per percent.
 * - **Cancel** (the notification's action, or the app swiped away): the copy stops within a chunk
 *   and the partial file is deleted.
 * - **Failure:** the partial file is deleted and a notification says so. No retry: the user asks
 *   again.
 *
 * Not unit-tested (excluded from coverage): it needs WorkManager, MediaStore and a server. The
 * copy ([copyRecording]), the names ([freeFileName]) and the work data ([toRecording]) are tested.
 */
@HiltWorker
class RecordingDownloadWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val tunnelCoordinator: TunnelCoordinator,
  private val fileSources: RecordingFileSources,
  private val outputs: DownloadOutputs,
  private val notifications: DownloadNotifications,
  @param:IoDispatcher private val io: CoroutineDispatcher
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val recording = inputData.toRecording() ?: return Result.failure(workDataOf(KEY_FAILURE to FAILURE_ERROR))
    if (!tunnelCoordinator.holds(TunnelHolder.Download(id.toString()))) {
      return Result.failure(workDataOf(KEY_FAILURE to FAILURE_INTERRUPTED))
    }
    setForeground(notifications.foregroundInfo(id, recording, percent = null))

    return withContext(io) {
      val files = fileSources.create()
      var pending: PendingDownload? = null
      try {
        files.open(recording).use { file ->
          val output = outputs.create(recording.fileName).also { pending = it }
          var lastPercent = -1
          val complete = copyRecording(
            file = file,
            output = output.stream,
            isStopped = { isStopped },
            onProgress = { copied, total ->
              val percent = (copied * 100 / total).toInt()
              if (percent != lastPercent) {
                lastPercent = percent
                setProgressAsync(workDataOf(KEY_PROGRESS to copied.toFloat() / total))
                notifications.showProgress(id, recording, percent)
              }
            }
          )
          if (!complete) throw CancellationException("Download stopped")
          val savedAs = output.publish()
          pending = null
          notifications.showFinished(id, recording, savedAs)
          Result.success(workDataOf(KEY_SAVED_AS to savedAs))
        }
      } catch (e: CancellationException) {
        withContext(NonCancellable) { pending?.discard() }
        throw e
      } catch (e: Exception) {
        Log.d(DEBUG_TAG, "Downloading ${recording.fileName} failed: $e")
        pending?.discard()
        notifications.showFailed(id, recording)
        Result.failure(workDataOf(KEY_FAILURE to FAILURE_ERROR))
      } finally {
        files.close()
      }
    }
  }
}
