package com.gdisys.cameras.core.downloads.data

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cash.turbine.test
import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.storage.domain.DownloadRecordsRepository
import com.gdisys.cameras.core.storage.domain.model.DownloadRecord
import com.gdisys.cameras.core.storage.domain.model.DownloadRecordState
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

private val RECORDING = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4")

@OptIn(ExperimentalCoroutinesApi::class)
class WorkManagerDownloadRepositoryTest {

  private val workManager = mockk<WorkManager>(relaxed = true)
  private val tunnelCoordinator = mockk<TunnelCoordinator>(relaxed = true)
  private val outputs = mockk<DownloadOutputs>(relaxed = true)
  private val records = FakeRecords()
  private val enqueued = slot<OneTimeWorkRequest>()

  private fun TestScope.repository() = WorkManagerDownloadRepository(
    workManager,
    tunnelCoordinator,
    records,
    outputs,
    backgroundScope,
    StandardTestDispatcher(testScheduler)
  )

  private class FakeRecords : DownloadRecordsRepository {
    val state = MutableStateFlow<List<DownloadRecord>>(emptyList())
    override val records: Flow<List<DownloadRecord>> = state
    override suspend fun update(transform: (List<DownloadRecord>) -> List<DownloadRecord>) {
      state.value = transform(state.value)
    }
  }

  private fun info(id: UUID, state: WorkInfo.State) = WorkInfo(id, state, setOf(DOWNLOAD_WORK_TAG))

  init {
    every { workManager.getWorkInfosForUniqueWorkFlow(downloadWorkName(RECORDING)) } returns flowOf(emptyList())
    every { workManager.enqueueUniqueWork(any(), any(), capture(enqueued)) } returns mockk(relaxed = true)
    every { tunnelCoordinator.holdIfUp(any()) } returns true
  }

  @Test
  fun `a download is enqueued as the recording's unique work, holding the tunnel`() = runTest {
    every { workManager.getWorkInfoByIdFlow(any()) } returns flowOf()

    assertEquals(DownloadRequestResult.STARTED, repository().download(RECORDING))

    val request = enqueued.captured
    verify { workManager.enqueueUniqueWork(downloadWorkName(RECORDING), ExistingWorkPolicy.KEEP, request) }
    verify { tunnelCoordinator.holdIfUp(TunnelHolder.Download(request.id.toString())) }
    assertEquals(RECORDING, request.workSpec.input.toRecording())
    assertTrue(DOWNLOAD_WORK_TAG in request.tags)
    assertEquals(listOf(newDownloadRecord(request.id.toString(), RECORDING)), records.state.value)
  }

  @Test
  fun `the holder is released once the work ends`() = runTest {
    val state = MutableStateFlow<WorkInfo?>(null)
    every { workManager.getWorkInfoByIdFlow(any()) } answers {
      val id = firstArg<UUID>()
      state.value = info(id, WorkInfo.State.RUNNING)
      state
    }
    repository().download(RECORDING)
    runCurrent()
    val holder = TunnelHolder.Download(enqueued.captured.id.toString())
    verify(exactly = 0) { tunnelCoordinator.release(any()) }

    state.value = info(enqueued.captured.id, WorkInfo.State.SUCCEEDED)
    runCurrent()

    verify { tunnelCoordinator.release(holder) }
  }

  @Test
  fun `the holder is released when the work is gone`() = runTest {
    every { workManager.getWorkInfoByIdFlow(any()) } returns flowOf(null)

    repository().download(RECORDING)
    runCurrent()

    verify { tunnelCoordinator.release(TunnelHolder.Download(enqueued.captured.id.toString())) }
  }

  @Test
  fun `a recording still downloading is not enqueued again`() = runTest {
    every { workManager.getWorkInfosForUniqueWorkFlow(downloadWorkName(RECORDING)) } returns
      flowOf(listOf(info(UUID.randomUUID(), WorkInfo.State.RUNNING)))

    assertEquals(DownloadRequestResult.ALREADY_RUNNING, repository().download(RECORDING))

    verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    verify(exactly = 0) { tunnelCoordinator.holdIfUp(any()) }
  }

  @Test
  fun `a finished download can be asked again`() = runTest {
    every { workManager.getWorkInfosForUniqueWorkFlow(downloadWorkName(RECORDING)) } returns
      flowOf(listOf(info(UUID.randomUUID(), WorkInfo.State.SUCCEEDED)))
    every { workManager.getWorkInfoByIdFlow(any()) } returns flowOf()

    assertEquals(DownloadRequestResult.STARTED, repository().download(RECORDING))
  }

  @Test
  fun `nothing is enqueued while the tunnel is down`() = runTest {
    every { tunnelCoordinator.holdIfUp(any()) } returns false

    assertEquals(DownloadRequestResult.TUNNEL_DOWN, repository().download(RECORDING))

    verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    assertEquals(emptyList<DownloadRecord>(), records.state.value)
  }

  // interrupted downloads

  private fun record(id: String, state: DownloadRecordState, partial: String? = null) =
    DownloadRecord(id, RECORDING.camera, RECORDING.fileName, state, partial)

  @Test
  fun `the interrupted downloads come from the records and the tunnel holders`() = runTest {
    every { tunnelCoordinator.holds(TunnelHolder.Download("live")) } returns true
    every { tunnelCoordinator.holds(TunnelHolder.Download("orphan")) } returns false
    records.state.value = listOf(
      record("live", DownloadRecordState.RUNNING),
      record("orphan", DownloadRecordState.RUNNING),
      record("failed", DownloadRecordState.FAILED)
    )

    repository().interrupted().test {
      assertEquals(
        listOf(
          InterruptedDownload("orphan", RECORDING, InterruptedDownload.Reason.INTERRUPTED),
          InterruptedDownload("failed", RECORDING, InterruptedDownload.Reason.FAILED)
        ),
        awaitItem()
      )
    }
  }

  @Test
  fun `discarding deletes the partial files and forgets only those downloads`() = runTest {
    records.state.value = listOf(
      record("a", DownloadRecordState.INTERRUPTED, partial = "content://media/1"),
      record("b", DownloadRecordState.FAILED),
      record("c", DownloadRecordState.RUNNING, partial = "content://media/3")
    )

    repository().discard(
      listOf(
        InterruptedDownload("a", RECORDING, InterruptedDownload.Reason.INTERRUPTED),
        InterruptedDownload("b", RECORDING, InterruptedDownload.Reason.FAILED)
      )
    )

    verify(exactly = 1) { outputs.deletePartial(any()) }
    verify { outputs.deletePartial("content://media/1") }
    assertEquals(listOf("c"), records.state.value.map { it.workId })
  }

  @Test
  fun `resuming discards and downloads the recordings again`() = runTest {
    every { workManager.getWorkInfoByIdFlow(any()) } returns flowOf()
    records.state.value = listOf(record("a", DownloadRecordState.INTERRUPTED, partial = "/sdcard/Download/Cameras/.a.part"))

    repository().resume(listOf(InterruptedDownload("a", RECORDING, InterruptedDownload.Reason.INTERRUPTED)))

    verify { outputs.deletePartial("/sdcard/Download/Cameras/.a.part") }
    verify { workManager.enqueueUniqueWork(downloadWorkName(RECORDING), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>()) }
    assertEquals(listOf(DownloadRecordState.RUNNING), records.state.value.map { it.state })
  }

  @Test
  fun `closing the app marks the running downloads interrupted, then cancels them all`() = runTest {
    records.state.value = listOf(record("a", DownloadRecordState.RUNNING), record("b", DownloadRecordState.FAILED))
    every { workManager.cancelAllWorkByTag(DOWNLOAD_WORK_TAG) } answers {
      // The workers react to the cancel, so the records must already be marked.
      assertEquals(
        listOf(DownloadRecordState.INTERRUPTED, DownloadRecordState.FAILED),
        records.state.value.map { it.state }
      )
      mockk(relaxed = true)
    }

    repository().interruptAll()

    verify { workManager.cancelAllWorkByTag(DOWNLOAD_WORK_TAG) }
  }

  @Test
  fun `the status follows the latest work of the recording`() = runTest {
    val infos = MutableStateFlow<List<WorkInfo>>(emptyList())
    every { workManager.getWorkInfosForUniqueWorkFlow(downloadWorkName(RECORDING)) } returns infos

    repository().observe(RECORDING).test {
      assertEquals(null, awaitItem())

      infos.value = listOf(info(UUID.randomUUID(), WorkInfo.State.FAILED), info(UUID.randomUUID(), WorkInfo.State.RUNNING))
      assertEquals(DownloadStatus.Running(), awaitItem())
    }
  }
}
