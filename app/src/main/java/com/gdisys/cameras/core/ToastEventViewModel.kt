package com.gdisys.cameras.core

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.components.ToastUiEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class ToastEventViewModel : ViewModel() {
  private val _toastUiEvent = Channel<ToastUiEvent>()
  val uiEvent: Flow<ToastUiEvent> = _toastUiEvent.receiveAsFlow()

  protected fun showToast(@StringRes resId: Int) {
    viewModelScope.launch {
      _toastUiEvent.send(ToastUiEvent.Show(resId))
    }
  }
}
