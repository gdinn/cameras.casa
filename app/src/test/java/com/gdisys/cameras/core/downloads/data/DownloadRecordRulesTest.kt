package com.gdisys.cameras.core.downloads.data

import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.storage.domain.model.DownloadRecord
import com.gdisys.cameras.core.storage.domain.model.DownloadRecordState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

private val RECORDING = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4")

private fun record(id: String, state: DownloadRecordState = DownloadRecordState.RUNNING, partial: String? = null) =
  DownloadRecord(id, RECORDING.camera, RECORDING.fileName, state, partial)

class DownloadRecordRulesTest {

  private val other = record("other", DownloadRecordState.FAILED)

  @Test
  fun `a new download is recorded as running, without a file yet`() {
    assertEquals(record("a"), newDownloadRecord("a", RECORDING))
  }

  @Test
  fun `the partial file is noted on its own record only`() {
    assertEquals(
      listOf(record("a", partial = "content://media/1"), other),
      listOf(record("a"), other).withPartialFile("a", "content://media/1")
    )
  }

  @Test
  fun `a success forgets the download`() {
    assertEquals(listOf(other), listOf(record("a", partial = "x"), other).afterSuccess("a"))
  }

  @Test
  fun `a user cancel forgets the download`() {
    assertEquals(listOf(other), listOf(record("a", partial = "x"), other).afterStop("a"))
  }

  @Test
  fun `a stop after the app closed keeps the download, without its deleted file`() {
    assertEquals(
      listOf(record("a", DownloadRecordState.INTERRUPTED), other),
      listOf(record("a", DownloadRecordState.INTERRUPTED, partial = "x"), other).afterStop("a")
    )
  }

  @Test
  fun `a failure is kept as failed, without its deleted file`() {
    assertEquals(
      listOf(record("a", DownloadRecordState.FAILED), other),
      listOf(record("a", partial = "x"), other).afterFailure("a")
    )
  }

  @Test
  fun `a re-run after a process death keeps the partial file to delete`() {
    assertEquals(
      listOf(record("a", DownloadRecordState.INTERRUPTED, partial = "x"), other),
      listOf(record("a", partial = "x"), other).interrupted("a")
    )
  }

  @Test
  fun `closing the app interrupts every running download, and only those`() {
    assertEquals(
      listOf(record("a", DownloadRecordState.INTERRUPTED), record("b", DownloadRecordState.INTERRUPTED), other),
      listOf(record("a"), record("b"), other).allRunningInterrupted()
    )
  }

  @Test
  fun `failed and interrupted downloads are reported, and so is a running one nobody holds`() {
    val records = listOf(
      record("failed", DownloadRecordState.FAILED),
      record("interrupted", DownloadRecordState.INTERRUPTED),
      record("orphan"),
      record("live")
    )

    assertEquals(
      listOf(
        InterruptedDownload("failed", RECORDING, InterruptedDownload.Reason.FAILED),
        InterruptedDownload("interrupted", RECORDING, InterruptedDownload.Reason.INTERRUPTED),
        InterruptedDownload("orphan", RECORDING, InterruptedDownload.Reason.INTERRUPTED)
      ),
      records.toInterruptedDownloads { it == "live" }
    )
  }

  @Test
  fun `a record that names no recording is not reported`() {
    val broken = DownloadRecord("x", "cam_10", "notes.txt", DownloadRecordState.FAILED)

    assertEquals(emptyList<InterruptedDownload>(), listOf(broken).toInterruptedDownloads { false })
  }
}
