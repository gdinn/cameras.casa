package com.gdisys.cameras.core.components

import android.content.Context
import android.content.res.Resources
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow

@Composable
fun ToastDisplayer(
  viewModel: ViewModel,
  toastUiEvent: Flow<ToastUiEvent>,
  context: Context,
  resources: Resources
) {
  LaunchedEffect(viewModel) {
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