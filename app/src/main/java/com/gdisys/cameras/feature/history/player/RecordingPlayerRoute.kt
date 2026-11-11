package com.gdisys.cameras.feature.history.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.history.player.components.RecordingPlayerScreen

/**
 * The permissions a download asks for: storage before Android 10, which the download cannot do
 * without. Notifications are asked in Config instead: a permission dialog pauses this screen, and
 * the ViewModel then holds the request until the screen and the tunnel are back.
 */
private fun downloadPermissions(): List<String> = buildList {
  if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

@Composable
fun RecordingPlayerRoute(
  viewModel: RecordingPlayerViewModel = hiltViewModel(),
  onNavigateBack: () -> Unit
) {
  val context = LocalContext.current
  val isGranted = { permission: String ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
    val storage = Manifest.permission.WRITE_EXTERNAL_STORAGE
    val storageGranted = Build.VERSION.SDK_INT > Build.VERSION_CODES.P || results[storage] == true || isGranted(storage)
    viewModel.onDownload(storageGranted)
  }
  val onDownload = {
    val missing = downloadPermissions().filterNot(isGranted)
    if (missing.isEmpty()) viewModel.onDownload(storageGranted = true) else permissionLauncher.launch(missing.toTypedArray())
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPaused() }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  RecordingPlayerScreen(
    uiState = uiState,
    video = { modifier -> viewModel.player.Video(modifier) },
    onBack = onNavigateBack,
    onPrevious = viewModel::onPrevious,
    onNext = viewModel::onNext,
    onRetry = viewModel::onRetry,
    onDownload = onDownload
  )
}
