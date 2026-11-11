package com.gdisys.cameras.core.downloads.data

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cash.turbine.test
import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
  private val enqueued = slot<OneTimeWorkRequest>()

  private fun TestScope.repository() = WorkManagerDownloadRepository(workManager, tunnelCoordinator, backgroundScope)

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
