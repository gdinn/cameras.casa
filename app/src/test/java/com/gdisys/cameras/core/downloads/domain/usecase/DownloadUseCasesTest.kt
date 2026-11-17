package com.gdisys.cameras.core.downloads.domain.usecase

import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.model.Recording
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class DownloadUseCasesTest {

  private val recording = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4")
  private val repository = mockk<DownloadRepository>()

  @Test
  fun `a download request goes to the repository`() = runTest {
    coEvery { repository.download(recording) } returns DownloadRequestResult.ALREADY_RUNNING

    assertEquals(DownloadRequestResult.ALREADY_RUNNING, DownloadRecordingUseCase(repository)(recording))
  }

  @Test
  fun `the download status comes from the repository`() = runTest {
    every { repository.observe(recording) } returns flowOf(DownloadStatus.Running(0.5f))

    assertEquals(DownloadStatus.Running(0.5f), ObserveRecordingDownloadUseCase(repository)(recording).first())
  }

  @Test
  fun `the interrupted downloads, resume, discard and interrupt go to the repository`() = runTest {
    val download = InterruptedDownload("a", recording, InterruptedDownload.Reason.FAILED)
    every { repository.interrupted() } returns flowOf(listOf(download))
    coEvery { repository.resume(any()) } returns listOf(DownloadRequestResult.STARTED)
    coEvery { repository.discard(any()) } returns Unit
    coEvery { repository.interruptAll() } returns Unit

    assertEquals(listOf(download), ObserveInterruptedDownloadsUseCase(repository)().first())
    assertEquals(listOf(DownloadRequestResult.STARTED), ResumeDownloadsUseCase(repository)(listOf(download)))
    DiscardDownloadsUseCase(repository)(listOf(download))
    InterruptDownloadsUseCase(repository)()

    coVerify { repository.resume(listOf(download)) }
    coVerify { repository.discard(listOf(download)) }
    coVerify { repository.interruptAll() }
  }
}
