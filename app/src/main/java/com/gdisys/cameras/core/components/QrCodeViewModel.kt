package com.gdisys.cameras.core.components

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.permission.domain.usecase.HasCameraPermissionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrCodeViewModel @Inject constructor(
  hasCameraPermissionUseCase: HasCameraPermissionUseCase
) : ToastEventViewModel() {

  private val _hasCameraPermission = MutableStateFlow(hasCameraPermissionUseCase())
  val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

  private val _qrCodeScannedEvent = Channel<String>()
  val qrCodeScannedEvent: Flow<String> = _qrCodeScannedEvent.receiveAsFlow()

  private var hasScanned = false

  fun onPermissionResult(granted: Boolean) {
    _hasCameraPermission.value = granted
  }

  fun resetScan() {
    hasScanned = false
  }

  fun onQrCodeScanned(rawValue: String) {
    if (hasScanned) return
    hasScanned = true
    viewModelScope.launch {
      _qrCodeScannedEvent.send(rawValue)
    }
  }

  fun onCameraInitError(error: Throwable) {
    Log.e(DEBUG_TAG, "Falha ao iniciar a câmera", error)
    showToast(QrCodeToastMessage.CAMERA_INIT_ERROR)
  }
}
