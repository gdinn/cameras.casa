package com.gdisys.cameras.core.downloads.data

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.recordings.domain.model.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

private val RECORDING = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-01.mp4")

class DownloadWorkTest {

  private fun info(state: WorkInfo.State, output: Data = Data.EMPTY, progress: Data = Data.EMPTY) =
    WorkInfo(UUID.randomUUID(), state, setOf(DOWNLOAD_WORK_TAG), output, progress)

  @Test
  fun `the input carries the recording there and back`() {
    assertEquals(RECORDING, downloadInput(RECORDING).toRecording())
  }

  @Test
  fun `an input that names no recording is refused`() {
    assertNull(Data.EMPTY.toRecording())
    assertNull(workDataOf(KEY_CAMERA to "cam_10").toRecording())
    assertNull(workDataOf(KEY_CAMERA to "cam_10", KEY_FILE_NAME to "notes.txt").toRecording())
  }

  @Test
  fun `each recording has its own unique work`() {
    val other = RECORDING.copy(fileName = "cam_10_2026-11-10_15-15-00.mp4")

    assertEquals("download:cam_10/cam_10_2026-11-10_15-00-01.mp4", downloadWorkName(RECORDING))
    assertNotEquals(downloadWorkName(RECORDING), downloadWorkName(other))
  }

  @Test
  fun `queued and blocked work is running without progress`() {
    assertEquals(DownloadStatus.Running(), info(WorkInfo.State.ENQUEUED).toDownloadStatus())
    assertEquals(DownloadStatus.Running(), info(WorkInfo.State.BLOCKED).toDownloadStatus())
  }

  @Test
  fun `running work carries its progress once known`() {
    assertEquals(DownloadStatus.Running(), info(WorkInfo.State.RUNNING).toDownloadStatus())
    assertEquals(
      DownloadStatus.Running(0.25f),
      info(WorkInfo.State.RUNNING, progress = workDataOf(KEY_PROGRESS to 0.25f)).toDownloadStatus()
    )
  }

  @Test
  fun `finished work says how it ended`() {
    assertEquals(
      DownloadStatus.Succeeded("a_1.mp4"),
      info(WorkInfo.State.SUCCEEDED, output = workDataOf(KEY_SAVED_AS to "a_1.mp4")).toDownloadStatus()
    )
    assertEquals(DownloadStatus.Failed, info(WorkInfo.State.FAILED).toDownloadStatus())
    assertNull(info(WorkInfo.State.CANCELLED).toDownloadStatus())
  }
}
