package com.gdisys.cameras.feature.config

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.permission.domain.usecase.HasCameraPermissionUseCase
import com.gdisys.cameras.core.permission.domain.usecase.HasVpnPermissionUseCase
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigStatusUseCase
import com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveUserPreferencesUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.RequestVpnPermissionUseCase
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
 * Payload-free on purpose: the consent `Intent` is a platform object, and routes — not ViewModels —
 * own platform round-trips. The ViewModel decides *whether* consent is needed; `ConfigRoute` builds
 * the `Intent` and launches it.
 */
sealed interface VpnPermissionUiEvent {
  data object RequestPermission : VpnPermissionUiEvent
}

@HiltViewModel
class ConfigViewModel @Inject constructor(
  private val saveUserPreferencesUseCase: SaveUserPreferencesUseCase,
  private val parseUserPreferencesFromQrCodeUseCase: ParseUserPreferencesFromQrCodeUseCase,
  getVpnConfigStatusUseCase: GetVpnConfigStatusUseCase,
  getStreamPreferencesUseCase: GetStreamPreferencesUseCase,
  hasVpnPermissionUseCase: HasVpnPermissionUseCase,
  private val hasCameraPermissionUseCase: HasCameraPermissionUseCase,
  private val requestVpnPermissionUseCase: RequestVpnPermissionUseCase
) : ToastEventViewModel() {

  private val _cameraPermissionButtonState = MutableStateFlow(
    if (hasCameraPermissionUseCase()) ConfigButtonState.Done else ConfigButtonState.Ready
  )
  private val _vpnPermissionButtonState = MutableStateFlow(
    if (hasVpnPermissionUseCase()) ConfigButtonState.Done else ConfigButtonState.Ready
  )
  private val _qrCodeError = MutableStateFlow(false)
  private val _canNavigateBackToHome = MutableStateFlow(false)

  // O estado de cada botão é derivado antes de compor o UiState: a sobrecarga tipada de `combine`
  // só vai até 5 fontes, e com as preferências de stream seriam 6.
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

  // Eagerly porque o gating da navegação consulta `uiState.value` no momento do clique: o estado
  // precisa estar atualizado mesmo em um instante sem coletores.
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
  }.stateIn(
    viewModelScope,
    SharingStarted.Eagerly,
    ConfigUiState()
  )

  private val _vpnPermissionUiEvent = Channel<VpnPermissionUiEvent>()
  val vpnPermissionUiEvent: Flow<VpnPermissionUiEvent> = _vpnPermissionUiEvent.receiveAsFlow()

  private val _requestCameraPermissionEvent = Channel<Unit>()
  val requestCameraPermissionEvent: Flow<Unit> = _requestCameraPermissionEvent.receiveAsFlow()

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
        Log.e(DEBUG_TAG, "QR Code scan error", e)
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
   * A Home só é alcançável com credenciais válidas, permissão de VPN concedida e ao menos uma
   * Stream URL. Faltando qualquer um, o feedback é o toast do primeiro requisito pendente.
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
   * Back consumido pela tela: ou falta um requisito, ou não há Home na pilha de navegação — nesse
   * caso o único caminho válido é o botão "Navigate to Home".
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
    // `RequestVpnPermissionUseCase` returns the consent Intent, or null when consent was already
    // given. Only that distinction matters here; the Intent itself is discarded and rebuilt by the
    // route, which is the layer allowed to touch platform types.
    val needsConsent = requestVpnPermissionUseCase() != null
    if (needsConsent) {
      viewModelScope.launch {
        _vpnPermissionUiEvent.send(VpnPermissionUiEvent.RequestPermission)
      }
    } else {
      showToast(ConfigToastMessage.PERMISSION_ALREADY_GRANTED)
    }
  }
}
