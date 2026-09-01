package com.gdisys.cameras.core.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.flow.Flow

@Composable
fun ToastDisplayer(
  toastUiEvent: Flow<ToastUiEvent>
) {
  val context = LocalContext.current
  val resources = LocalResources.current
  LaunchedEffect(toastUiEvent) {
    toastUiEvent.collect { event ->
      when (event) {
        is ToastUiEvent.Show -> {
          Toast.makeText(
            context,
            resources.getString(event.resId),
            Toast.LENGTH_SHORT
          ).show()
        }
      }
    }
  }
}

sealed interface ToastUiEvent {
  data class Show(val resId: Int): ToastUiEvent
}
