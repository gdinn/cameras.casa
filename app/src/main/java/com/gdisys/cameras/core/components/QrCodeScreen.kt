package com.gdisys.cameras.core.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gdisys.cameras.R
import com.gdisys.cameras.core.utils.QrCodeAnalyzer
import java.util.concurrent.ExecutorService

@Composable
fun QrCodeScreen(
  hasCameraPermission: Boolean,
  analyzerExecutor: ExecutorService,
  onRequestCameraPermission: () -> Unit,
  onQrCodeScanned: (String) -> Unit,
  onCameraInitError: (Throwable) -> Unit
) {
  // Renderiza a câmera se tem permissão, ou uma mensagem caso contrário
  if (hasCameraPermission) {
    QrCodeCameraPreview(
      analyzerExecutor = analyzerExecutor,
      onQrCodeScanned = onQrCodeScanned,
      onCameraInitError = onCameraInitError
    )
  } else {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(stringResource(R.string.qrcode_screen_camera_permission_required))
      Spacer(modifier = Modifier.height(16.dp))
      Button(onClick = onRequestCameraPermission) {
        Text(stringResource(R.string.qrcode_screen_camera_grant_access))
      }
    }
  }
}

@Composable
private fun QrCodeCameraPreview(
  modifier: Modifier = Modifier,
  analyzerExecutor: ExecutorService,
  onQrCodeScanned: (String) -> Unit,
  onCameraInitError: (Throwable) -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

  AndroidView(
    factory = { ctx ->
      val previewView = PreviewView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
      }

      val executor = ContextCompat.getMainExecutor(ctx)

      cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()

        // O executor é gerenciado pelo QrCodeViewModel (ver onCleared)
        imageAnalysis.setAnalyzer(
          analyzerExecutor,
          QrCodeAnalyzer(onQrCodeScanned = onQrCodeScanned)
        )

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
          )
        } catch (exc: Exception) {
          onCameraInitError(exc)
        }
      }, executor)

      previewView
    },
    modifier = modifier.fillMaxSize()
  )
}
