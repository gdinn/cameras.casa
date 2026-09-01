package com.gdisys.cameras.core.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class QrCodeViewModel @Inject constructor(
  @ApplicationContext context: Context
) : ToastEventViewModel() {

  private val _hasCameraPermission = MutableStateFlow(
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
  )
  val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

  private val _qrCodeScannedEvent = Channel<String>()
  val qrCodeScannedEvent: Flow<String> = _qrCodeScannedEvent.receiveAsFlow()

  val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()

  private var hasScanned = false

  fun onPermissionResult(granted: Boolean) {
    _hasCameraPermission.value = granted
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

  override fun onCleared() {
    super.onCleared()
    analyzerExecutor.shutdown()
  }
}
