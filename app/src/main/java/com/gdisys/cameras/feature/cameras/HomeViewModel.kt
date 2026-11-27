package com.gdisys.cameras.feature.cameras

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.downloads.domain.usecase.DiscardDownloadsUseCase
import com.gdisys.cameras.core.downloads.domain.usecase.ObserveInterruptedDownloadsUseCase
import com.gdisys.cameras.core.downloads.domain.usecase.ResumeDownloadsUseCase
import com.gdisys.cameras.core.recordings.domain.RecordingFileName
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.core.storage.domain.model.gridFor
import com.gdisys.cameras.core.storage.domain.model.orderFor
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.ObserveHasRecordingsAccessUseCase
import com.gdisys.cameras.core.storage.domain.usecase.UpdateStreamOrderUseCase
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import com.gdisys.cameras.core.webrtc.StreamConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.VideoSink
import javax.inject.Inject

private const val EXIT_CONFIRMATION_WINDOW_MS = 2000L

/** Home's registration with the [TunnelCoordinator]. */
internal val HOME_TUNNEL_HOLDER = TunnelHolder.Screen("home")

@HiltViewModel
class HomeViewModel @Inject constructor(
  observeVpnStateUseCase: ObserveVpnStateUseCase,
  getStreamPreferencesUseCase: GetStreamPreferencesUseCase,
  observeHasRecordingsAccessUseCase: ObserveHasRecordingsAccessUseCase,
  observeInterruptedDownloadsUseCase: ObserveInterruptedDownloadsUseCase,
  private val resumeDownloadsUseCase: ResumeDownloadsUseCase,
  private val discardDownloadsUseCase: DiscardDownloadsUseCase,
  private val updateStreamOrderUseCase: UpdateStreamOrderUseCase,
  private val tunnelCoordinator: TunnelCoordinator,
  private val streamConnectionRepository: StreamConnectionRepository
) : ToastEventViewModel() {
  private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState.Loading)
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private val _focusedStream = MutableStateFlow<String?>(null)

  /** URLs whose last connection attempt failed. A new attempt, or leaving the screen, clears one. */
  private val _failedStreams = MutableStateFlow<Set<String>>(emptySet())

  /** Fed by the UI, which is the only layer that can see the real device orientation. */
  private val _orientation = MutableStateFlow(StreamOrientation.PORTRAIT)

  private val _navigateUiEvent = Channel<HomeNavigateUiEvent>()
  val navigateUiEvent = _navigateUiEvent.receiveAsFlow()

  private var exitConfirmationJob: Job? = null

  /** Whether the route is resumed. `null` until the first lifecycle event. */
  private val _resumed = MutableStateFlow<Boolean?>(null)

  /**
   * Whether Home needs the tunnel: it is resumed *and* has a stream to show. Without URLs Home is
   * the empty screen, and a tunnel would carry nothing. `null` until the first lifecycle event.
   */
  private val tunnelWanted: StateFlow<Boolean?> = combine(
    _resumed,
    getStreamPreferencesUseCase().map { it.streamUrls.isNotEmpty() }
  ) { resumed, hasStreams -> resumed?.let { it && hasStreams } }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  /** The focused stream, and the camera whose history it offers to a user with a Samba login. */
  private val focus = combine(
    _focusedStream,
    observeHasRecordingsAccessUseCase()
  ) { stream, hasRecordingsAccess ->
    Focus(stream, stream?.takeIf { hasRecordingsAccess }?.let(RecordingFileName::cameraOfStreamUrl))
  }

  /** Downloads the user already answered for (or set aside) on this screen: not asked again. */
  private val _handledDownloads = MutableStateFlow<Set<String>>(emptySet())

  /**
   * The downloads that did not end, to offer resuming or discarding them. Only users with a Samba
   * login download. The route shows them over a ready Home: resuming needs the tunnel up.
   */
  val interruptedDownloads: StateFlow<List<InterruptedDownload>> = combine(
    observeHasRecordingsAccessUseCase(),
    observeInterruptedDownloadsUseCase(),
    _handledDownloads
  ) { hasRecordingsAccess, downloads, handled ->
    if (hasRecordingsAccess) downloads.filterNot { it.id in handled } else emptyList()
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  init {
    viewModelScope.launch {
      combine(
        getStreamPreferencesUseCase(),
        _orientation,
        focus,
        _failedStreams,
        observeVpnStateUseCase()
      ) { preferences, orientation, currentFocus, failedStreams, vpn ->
        when {
          // With no URLs there is nothing to connect to, and waiting on the VPN would show a
          // spinner that never resolves. This case therefore comes before the tunnel state.
          preferences.streamUrls.isEmpty() -> HomeUiState.Empty

          vpn != VpnTunnelState.CONNECTED -> HomeUiState.Loading

          else -> {
            val streams = preferences.orderFor(orientation)
            HomeUiState.Ready(
              streams = streams,
              focusedStream = currentFocus.stream,
              grid = preferences.gridFor(orientation),
              orientation = orientation,
              failedStreams = failedStreams intersect streams.toSet(),
              historyCamera = currentFocus.historyCamera
            )
          }
        }
      }.collect {
        _uiState.value = it
      }
    }

    // The coordinator owns the tunnel: Home only says whether it needs it. Ordering, conflation
    // and the grace period between two screens are the coordinator's job.
    viewModelScope.launch {
      tunnelWanted.filterNotNull().collect { wanted ->
        if (wanted) tunnelCoordinator.request(HOME_TUNNEL_HOLDER) else tunnelCoordinator.release(HOME_TUNNEL_HOLDER)
      }
    }

    // A failure is Home's to report only while Home is on display; another screen may hold the
    // tunnel by then.
    viewModelScope.launch {
      tunnelCoordinator.connectFailures.collect { e ->
        if (_resumed.value != true) return@collect
        Log.d(DEBUG_TAG, e.message.toString())
        showToast(HomeToastMessage.VPN_CONNECTION_ERROR)
        _navigateUiEvent.send(HomeNavigateUiEvent.ToConfig)
      }
    }
  }

  /** Also the retry: the card's "Try again" disconnects and calls this again with the same sink. */
  fun connectStream(streamUrl: String, videoSink: VideoSink) {
    _failedStreams.update { it - streamUrl }
    streamConnectionRepository.connect(streamUrl, videoSink) {
      _failedStreams.update { it + streamUrl }
      showToast(HomeToastMessage.STREAM_CONNECTION_ERROR)
    }
  }

  fun disconnectStream(streamUrl: String) {
    streamConnectionRepository.disconnect(streamUrl)
    _failedStreams.update { it - streamUrl }
  }

  fun focusStream(url: String) {
    _focusedStream.value = url
  }

  fun clearFocusedStream() {
    _focusedStream.value = null
  }

  fun onOrientationChanged(orientation: StreamOrientation) {
    _orientation.value = orientation
  }

  /**
   * Persists the new order — **only** the current orientation's; the other one is left intact.
   * Called when the item is dropped, not on every movement of the drag.
   */
  fun onStreamsReordered(newOrder: List<String>) {
    viewModelScope.launch {
      updateStreamOrderUseCase(_orientation.value, newOrder).onFailure { e ->
        Log.e(DEBUG_TAG, "Failed to persist the stream order", e)
        showToast(HomeToastMessage.STREAM_ORDER_SAVE_ERROR)
      }
    }
  }

  fun onBackPressed() {
    val pendingExitConfirmation = exitConfirmationJob
    if (pendingExitConfirmation != null) {
      pendingExitConfirmation.cancel()
      exitConfirmationJob = null
      viewModelScope.launch { _navigateUiEvent.send(HomeNavigateUiEvent.ExitApp) }
    } else {
      showToast(HomeToastMessage.PRESS_BACK_AGAIN_TO_EXIT)
      exitConfirmationJob = viewModelScope.launch {
        delay(EXIT_CONFIRMATION_WINDOW_MS)
        exitConfirmationJob = null
      }
    }
  }

  /**
   * Downloads the interrupted downloads again. Those that could not start (the tunnel dropped, say)
   * stay recorded and are offered the next time Home opens.
   */
  fun onResumeDownloads() {
    val downloads = takeInterruptedDownloads()
    if (downloads.isEmpty()) return
    viewModelScope.launch {
      runCatching { resumeDownloadsUseCase(downloads) }
        .onSuccess { results ->
          showToast(
            if (results.all { it == DownloadRequestResult.STARTED }) {
              HomeToastMessage.DOWNLOADS_RESUMED
            } else {
              HomeToastMessage.DOWNLOADS_NOT_ALL_RESUMED
            }
          )
        }
        .onFailure { e ->
          Log.e(DEBUG_TAG, "Failed to resume the downloads", e)
          showToast(HomeToastMessage.DOWNLOADS_ACTION_ERROR)
        }
    }
  }

  /** Deletes what the interrupted downloads left and forgets them. */
  fun onDiscardDownloads() {
    val downloads = takeInterruptedDownloads()
    if (downloads.isEmpty()) return
    viewModelScope.launch {
      runCatching { discardDownloadsUseCase(downloads) }.onFailure { e ->
        Log.e(DEBUG_TAG, "Failed to discard the downloads", e)
        showToast(HomeToastMessage.DOWNLOADS_ACTION_ERROR)
      }
    }
  }

  /** Sets the question aside until Home is opened again; the downloads stay recorded. */
  fun onInterruptedDownloadsDismissed() {
    takeInterruptedDownloads()
  }

  /** The downloads on offer, marked handled so the dialog closes at once and is not answered twice. */
  private fun takeInterruptedDownloads(): List<InterruptedDownload> {
    val downloads = interruptedDownloads.value
    _handledDownloads.update { it + downloads.map(InterruptedDownload::id) }
    return downloads
  }

  fun onResumed() {
    _resumed.value = true
  }

  fun onPaused() {
    _resumed.value = false
  }

  override fun onCleared() {
    tunnelCoordinator.release(HOME_TUNNEL_HOLDER)
    streamConnectionRepository.closeAll()
    super.onCleared()
  }
}

private data class Focus(val stream: String?, val historyCamera: String?)

sealed interface HomeNavigateUiEvent {
  data object ToConfig : HomeNavigateUiEvent
  data object ExitApp : HomeNavigateUiEvent
}
