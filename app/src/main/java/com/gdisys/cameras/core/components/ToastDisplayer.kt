package com.gdisys.cameras.core.components

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.flow.Flow

/** Shows a toast message given by its string resource. */
fun interface ToastPresenter {
  fun show(@StringRes resId: Int)
}

/**
 * The [ToastPresenter] used by [ToastDisplayer]. `null`, the default, means the platform `Toast`.
 * Instrumented tests provide a recording presenter instead, because the toast is a system window
 * that Compose Test cannot see.
 */
val LocalToastPresenter = staticCompositionLocalOf<ToastPresenter?> { null }

@Composable
fun ToastDisplayer(
  toastUiEvent: Flow<ToastUiEvent>
) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val presenter = LocalToastPresenter.current ?: remember(context, resources) {
    ToastPresenter { resId ->
      Toast.makeText(
        context,
        resources.getString(resId),
        Toast.LENGTH_SHORT
      ).show()
    }
  }
  LaunchedEffect(toastUiEvent, presenter) {
    toastUiEvent.collect { event ->
      when (event) {
        is ToastUiEvent.Show -> presenter.show(event.resId)
      }
    }
  }
}

sealed interface ToastUiEvent {
  data class Show(val resId: Int): ToastUiEvent
}
