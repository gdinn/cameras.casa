package com.gdisys.cameras.feature.config

import android.content.Intent
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.permission.domain.NotificationPermission
import com.gdisys.cameras.core.permission.domain.VpnConsent
import com.gdisys.cameras.core.permission.domain.usecase.GetNotificationPermissionUseCase
import com.gdisys.cameras.core.permission.domain.usecase.HasCameraPermissionUseCase
import com.gdisys.cameras.core.permission.domain.usecase.HasVpnPermissionUseCase
import com.gdisys.cameras.core.permission.domain.usecase.RequestVpnPermissionUseCase
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigStatusUseCase
import com.gdisys.cameras.core.storage.domain.usecase.MarkNotificationPermissionRequestedUseCase
import com.gdisys.cameras.core.storage.domain.usecase.ObserveHasRecordingsAccessUseCase
import com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveUserPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val QR_CODE_RESULT_KEY = "qr_code_raw_json"

/**
 * Carries the consent `Intent` from `VpnPermissionRepository`, the single source of the VPN consent.
 * The ViewModel only passes it along; `ConfigRoute` launches it. The route never calls
 * `VpnService.prepare` itself, so a fake repository controls the whole round-trip in tests.
 */
sealed interface VpnPermissionUiEvent {
  data class RequestPermission(val intent: Intent) : VpnPermissionUiEvent
}

@HiltViewModel
class ConfigViewModel @Inject constructor(
  private val saveUserPreferencesUseCase: SaveUserPreferencesUseCase,
  private val parseUserPreferencesFromQrCodeUseCase: ParseUserPreferencesFromQrCodeUseCase,
  getVpnConfigStatusUseCase: GetVpnConfigStatusUseCase,
  getStreamPreferencesUseCase: GetStreamPreferencesUseCase,
  hasVpnPermissionUseCase: HasVpnPermissionUseCase,
  private val hasCameraPermissionUseCase: HasCameraPermissionUseCase,
  private val requestVpnPermissionUseCase: RequestVpnPermissionUseCase,
  observeHasRecordingsAccessUseCase: ObserveHasRecordingsAccessUseCase,
  private val getNotificationPermissionUseCase: GetNotificationPermissionUseCase,
  private val markNotificationPermissionRequestedUseCase: MarkNotificationPermissionRequestedUseCase
) : ToastEventViewModel() {

  private val _cameraPermissionButtonState = MutableStateFlow(
    if (hasCameraPermissionUseCase()) ConfigButtonState.Done else ConfigButtonState.Ready
  )
  private val _vpnPermissionButtonState = MutableStateFlow(
    if (hasVpnPermissionUseCase()) ConfigButtonState.Done else ConfigButtonState.Ready
  )
  private val _notificationPermission = MutableStateFlow(getNotificationPermissionUseCase())
  private val _qrCodeError = MutableStateFlow(false)
  private val _canNavigateBackToHome = MutableStateFlow(false)

  // Each button's state is derived before the UiState is composed: the typed `combine` overload
  // tops out at 5 sources, and the stream preferences would make 6.
  private val qrCodeButtonState: Flow<ConfigButtonState> = combine(
    getVpnConfigStatusUseCase(),
    _qrCodeError
  ) { vpnConfigStatus, qrCodeError ->
    when {
      qrCodeError -> ConfigButtonState.Error
      vpnConfigStatus is VpnCredentialsStatus.Loading -> ConfigButtonState.Loading
      vpnConfigStatus is VpnCredentialsStatus.Loaded && vpnConfigStatus.hasValidCredentials -> ConfigButtonState.Done
      else -> ConfigButtonState.Ready
    }
  }

  private val streamURLsButtonState: Flow<ConfigButtonState> = getStreamPreferencesUseCase()
    .map { streamPreferences ->
      if (streamPreferences.streamUrls.isNotEmpty()) ConfigButtonState.Done else ConfigButtonState.Ready
    }
    .onStart { emit(ConfigButtonState.Loading) }

  /**
   * The notifications step: for users with a Samba login (only they download) on Android 13+. It
   * is asked once and the answer stands, so a declined permission stays declined (`Error`) instead
   * of asking again.
   * Asking here, and not when a download starts, matters: the permission dialog pauses the screen,
   * and the player gives the tunnel back on pause.
   */
  private val notificationPermissionButtonState: Flow<ConfigButtonState?> = combine(
    observeHasRecordingsAccessUseCase(),
    _notificationPermission,
    getStreamPreferencesUseCase().map { it.notificationPermissionRequested }
  ) { hasRecordingsAccess, permission, requested ->
    when {
      !hasRecordingsAccess || permission == NotificationPermission.NOT_REQUIRED -> null
      permission == NotificationPermission.GRANTED -> ConfigButtonState.Done
      requested -> ConfigButtonState.Error
      else -> ConfigButtonState.Ready
    }
  }

  // Eagerly, because the navigation gate reads `uiState.value` synchronously at click time: the
  // state has to be current even at a moment when nothing is collecting.
  val uiState: StateFlow<ConfigUiState> = combine(
    _cameraPermissionButtonState,
    qrCodeButtonState,
    _vpnPermissionButtonState,
    streamURLsButtonState,
    _canNavigateBackToHome
  ) { cameraPermissionButtonState, qrCodeButtonState, vpnPermissionButtonState, streamURLsButtonState, canNavigateBackToHome ->
    ConfigUiState(
      cameraPermissionButtonState = cameraPermissionButtonState,
      qrCodeButtonState = qrCodeButtonState,
      vpnPermissionButtonState = vpnPermissionButtonState,
      streamURLsButtonState = streamURLsButtonState,
      canNavigateBackToHome = canNavigateBackToHome
    )
  }.combine(notificationPermissionButtonState) { state, notifications ->
    state.copy(notificationPermissionButtonState = notifications)
  }.stateIn(
    viewModelScope,
    SharingStarted.Eagerly,
    ConfigUiState()
  )

  private val _vpnPermissionUiEvent = Channel<VpnPermissionUiEvent>()
  val vpnPermissionUiEvent: Flow<VpnPermissionUiEvent> = _vpnPermissionUiEvent.receiveAsFlow()

  private val _requestCameraPermissionEvent = Channel<Unit>()
  val requestCameraPermissionEvent: Flow<Unit> = _requestCameraPermissionEvent.receiveAsFlow()

  private val _requestNotificationPermissionEvent = Channel<Unit>()
  val requestNotificationPermissionEvent: Flow<Unit> = _requestNotificationPermissionEvent.receiveAsFlow()

  private val _navigateToScannerEvent = Channel<Unit>()
  val navigateToScannerEvent: Flow<Unit> = _navigateToScannerEvent.receiveAsFlow()

  private val _navigateToHomeEvent = Channel<Unit>()
  val navigateToHomeEvent: Flow<Unit> = _navigateToHomeEvent.receiveAsFlow()

  fun onShowScanner() {
    if (_cameraPermissionButtonState.value != ConfigButtonState.Done) {
      showToast(ConfigToastMessage.CAMERA_PERMISSION_REQUIRED_FOR_QR_CODE)
      return
    }
    viewModelScope.launch {
      _navigateToScannerEvent.send(Unit)
    }
  }

  fun onRequestCameraPermission() {
    if (_cameraPermissionButtonState.value == ConfigButtonState.Done) {
      showToast(ConfigToastMessage.CAMERA_PERMISSION_ALREADY_GRANTED)
      return
    }
    viewModelScope.launch {
      _requestCameraPermissionEvent.send(Unit)
    }
  }

  fun onCameraPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
    _cameraPermissionButtonState.value = if (granted) ConfigButtonState.Done else ConfigButtonState.Ready
    if (!granted && !shouldShowRationale) {
      showToast(ConfigToastMessage.CAMERA_PERMISSION_PERMANENTLY_DENIED)
    }
  }

  fun refreshCameraPermissionState() {
    _cameraPermissionButtonState.value =
      if (hasCameraPermissionUseCase()) ConfigButtonState.Done else ConfigButtonState.Ready
  }

  fun onRequestNotificationPermission() {
    when (uiState.value.notificationPermissionButtonState) {
      ConfigButtonState.Done -> showToast(ConfigToastMessage.NOTIFICATION_PERMISSION_ALREADY_GRANTED)
      ConfigButtonState.Error -> showToast(ConfigToastMessage.NOTIFICATION_PERMISSION_DECLINED)
      ConfigButtonState.Ready -> viewModelScope.launch { _requestNotificationPermissionEvent.send(Unit) }
      ConfigButtonState.Loading, null -> Unit
    }
  }

  /** Whatever the answer, it was asked: it is never asked again. */
  fun onNotificationPermissionResult(granted: Boolean) {
    refreshNotificationPermissionState()
    viewModelScope.launch {
      markNotificationPermissionRequestedUseCase().onFailure { e ->
        Log.e(DEBUG_TAG, "Failed to record the notification request", e)
      }
    }
    if (!granted) showToast(ConfigToastMessage.NOTIFICATION_PERMISSION_DECLINED)
  }

  /** The permission can change in the system settings while this screen is in the background. */
  fun refreshNotificationPermissionState() {
    _notificationPermission.value = getNotificationPermissionUseCase()
  }

  fun onVpnPermissionAccepted() {
    _vpnPermissionButtonState.value = ConfigButtonState.Done
    showToast(ConfigToastMessage.VPN_PERMISSION_ACCEPTED)
  }

  fun onVpnPermissionDenied() {
    showToast(ConfigToastMessage.VPN_PERMISSION_DENIED)
  }

  fun onQrCodeScanned(rawJson: String) {
    _qrCodeError.value = false
    parseUserPreferencesFromQrCodeUseCase(rawJson).fold(
      onSuccess = { userPreferences ->
        if (userPreferences == null) {
          _qrCodeError.value = true
          updateUserPreferences(UserPreferences())
          showToast(ConfigToastMessage.QR_CODE_INVALID_DATA_ERROR)
        } else {
          updateUserPreferences(userPreferences, showSuccessToast = true)
        }
      },
      onFailure = { e ->
        // Only the exception type: kotlinx.serialization quotes the offending JSON input in its
        // messages, and that input is the QR payload with the VPN private key in it.
        Log.e(DEBUG_TAG, "QR Code scan error: ${e::class.simpleName}")
        _qrCodeError.value = true
        updateUserPreferences(UserPreferences())
        showToast(ConfigToastMessage.QR_CODE_FORMAT_ERROR)
      }
    )
  }

  private fun updateUserPreferences(userPreferences: UserPreferences, showSuccessToast: Boolean = false) {
    viewModelScope.launch {
      saveUserPreferencesUseCase(userPreferences)
        .onSuccess {
          if (showSuccessToast) showToast(ConfigToastMessage.QR_CODE_LOADED_SUCCESSFULLY)
        }
        .onFailure { e ->
          Log.e(DEBUG_TAG, "Failed to save user preferences", e)
          _qrCodeError.value = true
          showToast(ConfigToastMessage.SAVE_PREFERENCES_ERROR)
        }
    }
  }

  fun setCanNavigateBackToHome(canNavigateBackToHome: Boolean) {
    _canNavigateBackToHome.value = canNavigateBackToHome
  }

  /**
   * Home is only reachable with valid credentials, VPN permission granted and at least one stream
   * URL. If any is missing, the feedback is a toast for the first unmet requirement.
   */
  fun onNavigateToHomeRequested() {
    val missingRequirement = uiState.value.missingRequirement
    if (missingRequirement != null) {
      showToast(missingRequirement.toToastMessage())
      return
    }
    viewModelScope.launch {
      _navigateToHomeEvent.send(Unit)
    }
  }

  /**
   * Back press consumed by this screen: either a requirement is missing, or there is no Home in
   * the back stack — in which case the only valid way out is the "Navigate to Home" button.
   */
  fun onBackPressedBlocked() {
    val missingRequirement = uiState.value.missingRequirement
    showToast(missingRequirement?.toToastMessage() ?: ConfigToastMessage.NAVIGATE_TO_HOME_REQUIRED)
  }

  private fun ConfigRequirement.toToastMessage(): ConfigToastMessage = when (this) {
    ConfigRequirement.VPN_CREDENTIALS -> ConfigToastMessage.VPN_CREDENTIALS_MISSING
    ConfigRequirement.VPN_PERMISSION -> ConfigToastMessage.VPN_PERMISSION_MISSING
    ConfigRequirement.STREAM_URLS -> ConfigToastMessage.STREAM_URLS_MISSING
  }

  fun acceptVpnPermission() {
    // The consent Intent travels in the event, so the route launches exactly what the repository
    // returned instead of asking the platform a second time.
    when (val consent = requestVpnPermissionUseCase()) {
      VpnConsent.Granted -> showToast(ConfigToastMessage.PERMISSION_ALREADY_GRANTED)
      is VpnConsent.Required -> viewModelScope.launch {
        _vpnPermissionUiEvent.send(VpnPermissionUiEvent.RequestPermission(consent.intent))
      }
    }
  }
}
