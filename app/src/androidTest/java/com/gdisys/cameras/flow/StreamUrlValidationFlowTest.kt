package com.gdisys.cameras.flow

import com.gdisys.cameras.R
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.network.STREAM_URL_HOST_PREFIX
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.robot.ConfigStep
import com.gdisys.cameras.testing.robot.config
import com.gdisys.cameras.testing.robot.streamUrls
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val PORT = "8889"
private const val STREAM_NAME = "cam_1"
private const val URL = "${STREAM_URL_HOST_PREFIX}$PORT/$STREAM_NAME"

/**
 * Scenario #6: adding a URL on the stream URLs screen, reached from Config. A rejected URL keeps
 * the form open and changes nothing; only saving persists.
 */
@HiltAndroidTest
class StreamUrlValidationFlowTest : FlowTest() {

  @Test
  fun invalidPortIsRejected() {
    openStreamUrls()

    streamUrls {
      addUrl(port = "70000", streamName = STREAM_NAME)

      assertAddFormOpen()
      assertNoUrls()
    }
  }

  @Test
  fun invalidStreamNameIsRejected() {
    openStreamUrls()

    streamUrls {
      addUrl(port = PORT, streamName = "cam 1")

      assertAddFormOpen()
      assertNoUrls()
    }
  }

  @Test
  fun duplicateUrlIsRejected() {
    seed.streams(listOf(URL))
    openStreamUrls()

    streamUrls {
      addUrl(port = PORT, streamName = STREAM_NAME)

      assertAddFormOpen()
      assertRow(URL)
    }
    assertEquals(listOf(URL), storedStreamPreferences().streamUrls)
  }

  @Test
  fun validUrlIsPersistedOnSave() {
    openStreamUrls()

    streamUrls {
      addUrl(port = PORT, streamName = STREAM_NAME)
      assertRow(URL)
      saveUrls()
    }
    assertEquals(listOf(URL), storedStreamPreferences().streamUrls)

    streamUrls { back() }
    config { assertStep(ConfigStep.STREAM_URLS, ConfigButtonState.Done) }
  }

  @Test
  fun unsavedChangesAskBeforeLeaving() {
    openStreamUrls()

    streamUrls {
      addUrl(port = PORT, streamName = STREAM_NAME)
      back()
      assertDialog(R.string.stream_urls_dialog_discard_title)
      confirmDialog()
    }

    assertEquals(listOf(NavigationRoute.Loading, NavigationRoute.Config), backStackRoutes())
    assertEquals(emptyList<String>(), storedStreamPreferences().streamUrls)
    config { assertStep(ConfigStep.STREAM_URLS, ConfigButtonState.Ready) }
  }

  private fun openStreamUrls() {
    launchApp()
    config { tapStep(ConfigStep.STREAM_URLS) }
  }
}
