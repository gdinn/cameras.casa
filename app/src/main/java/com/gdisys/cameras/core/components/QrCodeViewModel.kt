package com.gdisys.cameras.core.components

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrCodeViewModel @Inject constructor() : ToastEventViewModel() {

  private val _qrCodeScannedEvent = Channel<String>()
  val qrCodeScannedEvent: Flow<String> = _qrCodeScannedEvent.receiveAsFlow()

  private val _navigateBackEvent = Channel<Unit>()
  val navigateBackEvent: Flow<Unit> = _navigateBackEvent.receiveAsFlow()

  private var hasScanned = false

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
    viewModelScope.launch {
      _navigateBackEvent.send(Unit)
    }
  }

  fun onBackPressed() {
    showToast(QrCodeToastMessage.READING_CANCELLED)
    viewModelScope.launch {
      _navigateBackEvent.send(Unit)
    }
  }
}
