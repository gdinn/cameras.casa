package com.gdisys.cameras.core.downloads.data

import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.RecordingFileName
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.storage.domain.model.DownloadRecord
import com.gdisys.cameras.core.storage.domain.model.DownloadRecordState

/*
 * How each moment of a download changes its record. A record exists from the request until the
 * download ends well (or is cancelled by the user), so a process that dies leaves it behind.
 */

internal fun newDownloadRecord(workId: String, recording: Recording) =
  DownloadRecord(workId, recording.camera, recording.fileName, DownloadRecordState.RUNNING)

/** The unfinished file exists, at [partialFile]: what to delete if the process dies. */
internal fun List<DownloadRecord>.withPartialFile(workId: String, partialFile: String) =
  map { if (it.workId == workId) it.copy(partialFile = partialFile) else it }

internal fun List<DownloadRecord>.afterSuccess(workId: String) = filterNot { it.workId == workId }

/**
 * The work was stopped and its partial file deleted. Still running in the record means the user
 * cancelled it: forgotten. Already interrupted means the app was closed: kept, to be reported.
 */
internal fun List<DownloadRecord>.afterStop(workId: String) = mapNotNull {
  when {
    it.workId != workId -> it
    it.state == DownloadRecordState.RUNNING -> null
    else -> it.copy(partialFile = null)
  }
}

/** A read or write failure; the partial file is deleted. */
internal fun List<DownloadRecord>.afterFailure(workId: String) =
  map { if (it.workId == workId) it.copy(state = DownloadRecordState.FAILED, partialFile = null) else it }

/** The worker found no tunnel holder: the process died. The partial file it left is still there. */
internal fun List<DownloadRecord>.interrupted(workId: String) =
  map { if (it.workId == workId) it.copy(state = DownloadRecordState.INTERRUPTED) else it }

internal fun List<DownloadRecord>.allRunningInterrupted() =
  map { if (it.state == DownloadRecordState.RUNNING) it.copy(state = DownloadRecordState.INTERRUPTED) else it }

/**
 * The records to report. A record still running whose tunnel holder this process does not hold
 * ([isHeld]) was left by a process that died. A record that names no recording is dropped: there
 * is nothing to resume.
 */
internal fun List<DownloadRecord>.toInterruptedDownloads(isHeld: (workId: String) -> Boolean): List<InterruptedDownload> =
  mapNotNull { record ->
    val reason = when (record.state) {
      DownloadRecordState.FAILED -> InterruptedDownload.Reason.FAILED
      DownloadRecordState.INTERRUPTED -> InterruptedDownload.Reason.INTERRUPTED
      DownloadRecordState.RUNNING -> if (isHeld(record.workId)) null else InterruptedDownload.Reason.INTERRUPTED
    } ?: return@mapNotNull null
    val start = RecordingFileName.parse(record.camera, record.fileName) ?: return@mapNotNull null
    InterruptedDownload(record.workId, Recording(record.camera, start, record.fileName), reason)
  }
