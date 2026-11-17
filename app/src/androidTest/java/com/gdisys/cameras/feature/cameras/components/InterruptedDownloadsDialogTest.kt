package com.gdisys.cameras.feature.cameras.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.testing.setThemedContent
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

private val DOWNLOADS = listOf(
  InterruptedDownload(
    "a",
    Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4"),
    InterruptedDownload.Reason.INTERRUPTED
  ),
  InterruptedDownload(
    "b",
    Recording("cam_11", LocalDateTime.of(2026, 11, 9, 8, 15), "cam_11_2026-11-09_08-15-00.mp4"),
    InterruptedDownload.Reason.FAILED
  )
)

@RunWith(AndroidJUnit4::class)
class InterruptedDownloadsDialogTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val calls = CopyOnWriteArrayList<String>()

  private fun setDialog() = composeRule.setThemedContent {
    InterruptedDownloadsDialog(
      downloads = DOWNLOADS,
      onResume = { calls += "resume" },
      onDiscard = { calls += "discard" },
      onDismiss = { calls += "dismiss" }
    )
  }

  @Test
  fun eachDownloadIsListedWithItsReason() {
    setDialog()

    composeRule.onNodeWithTag(HomeTestTags.INTERRUPTED_DOWNLOADS_DIALOG).assertIsDisplayed()
    composeRule.onNodeWithText("cam_10", substring = true).assertIsDisplayed()
    composeRule.onNodeWithText(string(R.string.home_interrupted_downloads_reason_interrupted), substring = true)
      .assertIsDisplayed()
    composeRule.onNodeWithText(string(R.string.home_interrupted_downloads_reason_failed), substring = true)
      .assertIsDisplayed()
    assertEquals(1, composeRule.onAllNodes(hasText("cam_11", substring = true)).fetchSemanticsNodes().size)
  }

  @Test
  fun resumeAndDiscardAreReported() {
    setDialog()

    composeRule.onNodeWithTag(HomeTestTags.RESUME_DOWNLOADS_BUTTON).performClick()
    composeRule.onNodeWithTag(HomeTestTags.DISCARD_DOWNLOADS_BUTTON).performClick()

    assertEquals(listOf("resume", "discard"), calls)
  }
}
