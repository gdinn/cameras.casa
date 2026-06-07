package com.gdisys.cameras.feature.config.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gdisys.cameras.feature.config.QrCodeAnalyzer
import java.util.concurrent.Executors

@Composable
fun QrCodeScreen(
  onCodeScanned: (String) -> Unit
) {
  val context = LocalContext.current

  // Estado que guarda se a permissão foi concedida
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
      ) == PackageManager.PERMISSION_GRANTED
    )
  }

  // Launcher para solicitar a permissão
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted ->
      hasCameraPermission = granted
    }
  )

  // Solicita a permissão assim que a tela abre, caso ainda não tenha
  LaunchedEffect(key1 = true) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  // Renderiza a câmera se tem permissão, ou uma mensagem caso contrário
  if (hasCameraPermission) {
    QrCodeScreenPreview(onQrCodeScanned = onCodeScanned)
  } else {
    Column(
      modifier = Modifier.Companion.fillMaxSize(),
      horizontalAlignment = Alignment.Companion.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text("A permissão da câmera é necessária para ler o QR Code.")
      Spacer(modifier = Modifier.Companion.height(16.dp))
      Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
        Text("Conceder Permissão")
      }
    }
  }
}

@Composable
private fun QrCodeScreenPreview(
  modifier: Modifier = Modifier.Companion,
  onQrCodeScanned: (String) -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val cameraProviderFuture = remember { ProcessCameraProvider.Companion.getInstance(context) }

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
          it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()

        val analyzerExecutor = Executors.newSingleThreadExecutor()

        // Passamos o callback para a nossa classe Analyzer
        imageAnalysis.setAnalyzer(
          analyzerExecutor,
          QrCodeAnalyzer(onQrCodeScanned = { result ->
            onQrCodeScanned(result)
          })
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
          Log.e("QrCodeScanner", "Falha ao iniciar a câmera", exc)
        }
      }, executor)

      previewView
    },
    modifier = modifier.fillMaxSize()
  )
}
