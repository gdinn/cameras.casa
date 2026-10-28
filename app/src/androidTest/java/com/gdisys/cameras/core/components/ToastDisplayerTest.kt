package com.gdisys.cameras.core.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.feature.config.ConfigToastMessage
import com.gdisys.cameras.testing.RecordingToastPresenter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToastDisplayerTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun eachEventReachesTheProvidedPresenterInOrder() {
    val events = Channel<ToastUiEvent>(Channel.UNLIMITED)
    val toastUiEvent = events.receiveAsFlow()
    val presenter = RecordingToastPresenter()
    composeRule.setContent {
      CompositionLocalProvider(LocalToastPresenter provides presenter) {
        ToastDisplayer(toastUiEvent = toastUiEvent)
      }
    }

    events.trySend(ToastUiEvent.Show(ConfigToastMessage.VPN_PERMISSION_ACCEPTED.resId))
    events.trySend(ToastUiEvent.Show(ConfigToastMessage.VPN_PERMISSION_DENIED.resId))

    composeRule.waitUntil { presenter.shown.size == 2 }
    assertEquals(
      listOf(
        ConfigToastMessage.VPN_PERMISSION_ACCEPTED.resId,
        ConfigToastMessage.VPN_PERMISSION_DENIED.resId
      ),
      presenter.shown.toList()
    )
  }
}
