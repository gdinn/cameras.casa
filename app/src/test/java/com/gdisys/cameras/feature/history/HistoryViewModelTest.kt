package com.gdisys.cameras.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.usecase.GetRecordingDaysUseCase
import com.gdisys.cameras.core.recordings.domain.usecase.GetRecordingMonthsUseCase
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

private const val CAM = "cam_10"
private val NOV: YearMonth = YearMonth.of(2026, 11)
private val OCT: YearMonth = YearMonth.of(2026, 10)
private val NOV_DAYS = listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10))

class HistoryViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val vpnState = MutableStateFlow(VpnTunnelState.DISCONNECTED)
  private val observeVpnStateUseCase = mockk<ObserveVpnStateUseCase>()
  private val getRecordingMonthsUseCase = mockk<GetRecordingMonthsUseCase>()
  private val getRecordingDaysUseCase = mockk<GetRecordingDaysUseCase>()
  private val tunnelCoordinator = FakeTunnelCoordinator()

  private lateinit var viewModel: HistoryViewModel

  @Before
  fun setUp() {
    every { observeVpnStateUseCase() } returns vpnState
    coEvery { getRecordingMonthsUseCase(CAM) } returns Result.success(listOf(NOV, OCT))
    coEvery { getRecordingDaysUseCase(CAM, NOV) } returns Result.success(NOV_DAYS)
    viewModel = newViewModel()
  }

  private fun newViewModel() = HistoryViewModel(
    SavedStateHandle(mapOf(HISTORY_CAMERA_ARG to CAM)),
    observeVpnStateUseCase,
    getRecordingMonthsUseCase,
    getRecordingDaysUseCase,
    tunnelCoordinator
  )

  private val content get() = viewModel.uiState.value.content

  private fun months(vararg items: MonthItem) = HistoryContent.Months(items.toList())

  /** On screen with the tunnel up and the months listed. */
  private fun open() {
    viewModel.onResumed()
    vpnState.value = VpnTunnelState.CONNECTED
  }

  // tunnel

  @Test
  fun `the tunnel is requested on resume and released on pause`() {
    viewModel.onResumed()
    viewModel.onPaused()

    assertEquals(listOf("request", "release"), tunnelCoordinator.calls)
  }

  @Test
  fun `the tunnel is released when the screen is gone`() {
    val store = ViewModelStore()
    store.put("history", viewModel)

    store.clear()

    assertEquals(listOf("release"), tunnelCoordinator.calls)
  }

  @Test
  fun `the camera comes from the route`() {
    assertEquals(CAM, viewModel.uiState.value.camera)
  }

  @Test
  fun `a missing camera is a programming error`() {
    assertThrows(IllegalStateException::class.java) {
      HistoryViewModel(
        SavedStateHandle(),
        observeVpnStateUseCase,
        getRecordingMonthsUseCase,
        getRecordingDaysUseCase,
        tunnelCoordinator
      )
    }
  }

  // months

  @Test
  fun `the months wait for the tunnel`() {
    viewModel.onResumed()

    assertEquals(HistoryContent.Loading, content)
    coVerify(exactly = 0) { getRecordingMonthsUseCase(any()) }

    vpnState.value = VpnTunnelState.CONNECTED

    assertEquals(months(MonthItem(NOV), MonthItem(OCT)), content)
  }

  @Test
  fun `the months are listed once, even when the tunnel comes back`() {
    open()

    vpnState.value = VpnTunnelState.DISCONNECTED
    vpnState.value = VpnTunnelState.CONNECTED

    coVerify(exactly = 1) { getRecordingMonthsUseCase(CAM) }
  }

  @Test
  fun `a camera without recordings is empty`() {
    coEvery { getRecordingMonthsUseCase(CAM) } returns Result.success(emptyList())

    open()

    assertEquals(HistoryContent.Empty, content)
  }

  @Test
  fun `each listing failure has its own message`() {
    mapOf(
      RecordingsException.NotAdmin() to HistoryError.NOT_ADMIN,
      RecordingsException.AuthenticationFailed(IOException()) to HistoryError.AUTHENTICATION_FAILED,
      RecordingsException.Unreachable(IOException()) to HistoryError.UNREACHABLE,
      RecordingsException.Failed(IOException()) to HistoryError.FAILED,
      IllegalArgumentException() to HistoryError.FAILED
    ).forEach { (failure, error) ->
      coEvery { getRecordingMonthsUseCase(CAM) } returns Result.failure(failure)
      vpnState.value = VpnTunnelState.DISCONNECTED
      viewModel = newViewModel()

      open()

      assertEquals(failure.toString(), HistoryContent.Failed(error), content)
    }
  }

  @Test
  fun `retry lists the months again`() {
    coEvery { getRecordingMonthsUseCase(CAM) } returns Result.failure(RecordingsException.Unreachable(IOException()))
    open()
    coEvery { getRecordingMonthsUseCase(CAM) } returns Result.success(listOf(NOV))

    viewModel.onRetry()

    assertEquals(months(MonthItem(NOV)), content)
  }

  @Test
  fun `retry does nothing unless something failed`() {
    open()

    viewModel.onRetry()

    coVerify(exactly = 1) { getRecordingMonthsUseCase(CAM) }
  }

  @Test
  fun `a failed tunnel while waiting is reported, and retry asks for the tunnel again`() = runTest {
    viewModel.onResumed()

    tunnelCoordinator.failures.emit(IllegalStateException("handshake"))
    assertEquals(HistoryContent.Failed(HistoryError.VPN_CONNECTION), content)

    viewModel.onRetry()
    assertEquals(HistoryContent.Loading, content)
    assertEquals(listOf("request", "request"), tunnelCoordinator.calls)

    vpnState.value = VpnTunnelState.CONNECTED
    assertEquals(months(MonthItem(NOV), MonthItem(OCT)), content)
  }

  @Test
  fun `a failed tunnel is ignored while paused or once the months are listed`() = runTest {
    tunnelCoordinator.failures.emit(IllegalStateException("handshake"))
    assertEquals(HistoryContent.Loading, content)

    open()
    tunnelCoordinator.failures.emit(IllegalStateException("handshake"))
    assertEquals(months(MonthItem(NOV), MonthItem(OCT)), content)
  }

  // days

  @Test
  fun `expanding a month lists its days`() {
    open()

    viewModel.onMonthClicked(NOV)

    assertEquals(months(MonthItem(NOV, DaysState.Loaded(NOV_DAYS)), MonthItem(OCT)), content)
  }

  @Test
  fun `a month shows its loading state until the days arrive`() {
    val pending = CompletableDeferred<Result<List<LocalDate>>>()
    coEvery { getRecordingDaysUseCase(CAM, NOV) } coAnswers { pending.await() }
    open()

    viewModel.onMonthClicked(NOV)
    assertEquals(months(MonthItem(NOV, DaysState.Loading), MonthItem(OCT)), content)

    pending.complete(Result.success(NOV_DAYS))
    assertEquals(months(MonthItem(NOV, DaysState.Loaded(NOV_DAYS)), MonthItem(OCT)), content)
  }

  @Test
  fun `clicking an expanded month collapses it, and a late answer does not reopen it`() {
    val pending = CompletableDeferred<Result<List<LocalDate>>>()
    coEvery { getRecordingDaysUseCase(CAM, NOV) } coAnswers { pending.await() }
    open()
    viewModel.onMonthClicked(NOV)

    viewModel.onMonthClicked(NOV)
    pending.complete(Result.success(NOV_DAYS))

    assertEquals(months(MonthItem(NOV), MonthItem(OCT)), content)
  }

  @Test
  fun `the days are listed again each time a month is expanded`() {
    open()

    viewModel.onMonthClicked(NOV)
    viewModel.onMonthClicked(NOV)
    viewModel.onMonthClicked(NOV)

    coVerify(exactly = 2) { getRecordingDaysUseCase(CAM, NOV) }
  }

  @Test
  fun `months expand independently`() {
    coEvery { getRecordingDaysUseCase(CAM, OCT) } returns Result.success(emptyList())
    open()

    viewModel.onMonthClicked(NOV)
    viewModel.onMonthClicked(OCT)

    assertEquals(
      months(MonthItem(NOV, DaysState.Loaded(NOV_DAYS)), MonthItem(OCT, DaysState.Loaded(emptyList()))),
      content
    )
  }

  @Test
  fun `a month whose days failed can be retried`() {
    coEvery { getRecordingDaysUseCase(CAM, NOV) } returns Result.failure(RecordingsException.Unreachable(IOException()))
    open()
    viewModel.onMonthClicked(NOV)
    assertEquals(months(MonthItem(NOV, DaysState.Failed(HistoryError.UNREACHABLE)), MonthItem(OCT)), content)

    coEvery { getRecordingDaysUseCase(CAM, NOV) } returns Result.success(NOV_DAYS)
    viewModel.onRetryMonth(NOV)

    assertEquals(months(MonthItem(NOV, DaysState.Loaded(NOV_DAYS)), MonthItem(OCT)), content)
  }

  @Test
  fun `retrying a month that did not fail does nothing`() {
    open()
    viewModel.onMonthClicked(NOV)

    viewModel.onRetryMonth(NOV)
    viewModel.onRetryMonth(OCT)

    coVerify(exactly = 1) { getRecordingDaysUseCase(CAM, NOV) }
    coVerify(exactly = 0) { getRecordingDaysUseCase(CAM, OCT) }
  }

  @Test
  fun `months cannot be clicked before they are listed`() {
    viewModel.onMonthClicked(NOV)
    viewModel.onRetryMonth(NOV)

    assertEquals(HistoryContent.Loading, content)
    coVerify(exactly = 0) { getRecordingDaysUseCase(any(), any()) }
  }

  private class FakeTunnelCoordinator : TunnelCoordinator {
    val calls = mutableListOf<String>()
    val failures = MutableSharedFlow<Throwable>()
    override val connectFailures = failures

    override fun request(holder: TunnelHolder) {
      assertEquals(HISTORY_TUNNEL_HOLDER, holder)
      calls += "request"
    }

    override fun release(holder: TunnelHolder) {
      assertEquals(HISTORY_TUNNEL_HOLDER, holder)
      calls += "release"
    }

    override fun holdIfUp(holder: TunnelHolder) = throw UnsupportedOperationException()

    override fun clear() = throw UnsupportedOperationException()
  }
}
