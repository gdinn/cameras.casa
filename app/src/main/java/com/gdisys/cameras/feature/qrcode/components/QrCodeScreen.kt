package com.gdisys.cameras.feature.qrcode.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun QrCodeScreen(
  onQrCodeScanned: (String) -> Unit,
  onCameraInitError: (Throwable) -> Unit
) {
  // Camera permission is requested before this screen is shown, in ConfigScreen.
  QrCodeCameraPreview(
    onQrCodeScanned = onQrCodeScanned,
    onCameraInitError = onCameraInitError
  )
}

@Composable
private fun QrCodeCameraPreview(
  modifier: Modifier = Modifier,
  onQrCodeScanned: (String) -> Unit,
  onCameraInitError: (Throwable) -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

  // The executor belongs to the camera rendered here, so its lifecycle is managed in this
  // composable rather than in the ViewModel.
  val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
  DisposableEffect(Unit) {
    onDispose { analyzerExecutor.shutdown() }
  }

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
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) !=
          PackageManager.PERMISSION_GRANTED
        ) {
          // Permission may have been revoked externally, from system settings, while this
          // screen was already open.
          onCameraInitError(SecurityException("Camera permission not granted"))
          return@addListener
        }

        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()

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
