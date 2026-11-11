package com.gdisys.cameras.core.downloads.data

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.recordings.domain.RecordingFileName
import com.gdisys.cameras.core.recordings.domain.model.Recording

/** Every download's work carries this tag, so all of them can be found or cancelled at once. */
internal const val DOWNLOAD_WORK_TAG = "recording-download"

internal const val KEY_CAMERA = "camera"
internal const val KEY_FILE_NAME = "fileName"
internal const val KEY_PROGRESS = "progress"
internal const val KEY_SAVED_AS = "savedAs"
internal const val KEY_FAILURE = "failure"

/** The worker found no tunnel holder: the process that took it is gone. */
internal const val FAILURE_INTERRUPTED = "interrupted"
internal const val FAILURE_ERROR = "error"

/**
 * One unique work per recording, so a second request for a recording that is still downloading is
 * ignored (`ExistingWorkPolicy.KEEP`).
 */
internal fun downloadWorkName(recording: Recording): String =
  "download:${recording.camera}/${recording.fileName}"

internal fun downloadInput(recording: Recording): Data =
  workDataOf(KEY_CAMERA to recording.camera, KEY_FILE_NAME to recording.fileName)

/** The recording a download's input names, or `null` when the input is not one. */
internal fun Data.toRecording(): Recording? {
  val camera = getString(KEY_CAMERA) ?: return null
  val fileName = getString(KEY_FILE_NAME) ?: return null
  val start = RecordingFileName.parse(camera, fileName) ?: return null
  return Recording(camera, start, fileName)
}

internal fun WorkInfo.toDownloadStatus(): DownloadStatus? = when (state) {
  WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.Running()
  WorkInfo.State.RUNNING -> DownloadStatus.Running(
    progress.getFloat(KEY_PROGRESS, -1f).takeIf { it >= 0f }
  )
  WorkInfo.State.SUCCEEDED -> DownloadStatus.Succeeded(outputData.getString(KEY_SAVED_AS))
  WorkInfo.State.FAILED -> DownloadStatus.Failed
  WorkInfo.State.CANCELLED -> null
}
