package com.gdisys.cameras.feature.history.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.recordings.domain.usecase.RecordingDirection
import com.gdisys.cameras.feature.history.HistoryError
import com.gdisys.cameras.feature.history.player.PlayerStatus
import com.gdisys.cameras.feature.history.player.RecordingPlayerUiState
import com.gdisys.cameras.testing.setThemedContent
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

private val AT_1510 = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 10), "cam_10_2026-11-10_15-10-00.mp4")

/** The player screen with hand-written states, a placeholder for the video and recorded callbacks. */
@RunWith(AndroidJUnit4::class)
class RecordingPlayerScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val backs = AtomicInteger()
  private val previous = AtomicInteger()
  private val next = AtomicInteger()
  private val retries = AtomicInteger()

  private fun setScreen(state: RecordingPlayerUiState) {
    composeRule.setThemedContent {
      RecordingPlayerScreen(
        uiState = state,
        video = { modifier -> Box(modifier) },
        onBack = { backs.incrementAndGet() },
        onPrevious = { previous.incrementAndGet() },
        onNext = { next.incrementAndGet() },
        onRetry = { retries.incrementAndGet() }
      )
    }
  }

  @Test
  fun playingShowsTheVideoAndTheButtonsWork() {
    setScreen(RecordingPlayerUiState(AT_1510, nearestNotice = false, status = PlayerStatus.Playing))

    composeRule.onNodeWithTag(RecordingPlayerTestTags.VIDEO).assertIsDisplayed()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.LOADING).assertDoesNotExist()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.NEAREST_NOTICE).assertDoesNotExist()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.PREVIOUS_BUTTON).performClick()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.NEXT_BUTTON).performClick()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.BACK_BUTTON).performClick()

    assertEquals(listOf(1, 1, 1), listOf(previous.get(), next.get(), backs.get()))
  }

  @Test
  fun theNoticeSaysWhereTheRecordingStarts() {
    setScreen(RecordingPlayerUiState(AT_1510, nearestNotice = true))

    composeRule.onNodeWithTag(RecordingPlayerTestTags.NEAREST_NOTICE).assertIsDisplayed()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun lookingUpANeighborDisablesBothButtons() {
    setScreen(RecordingPlayerUiState(AT_1510, nearestNotice = false, looking = RecordingDirection.NEXT))

    composeRule.onNodeWithTag(RecordingPlayerTestTags.PREVIOUS_BUTTON).assertIsNotEnabled()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun aFailureShowsItsMessageAndRetries() {
    setScreen(RecordingPlayerUiState(AT_1510, nearestNotice = false, status = PlayerStatus.Failed(HistoryError.UNREACHABLE)))

    composeRule.onNodeWithText(string(R.string.history_error_unreachable)).assertIsDisplayed()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.RETRY_BUTTON).performClick()
    composeRule.onNodeWithTag(RecordingPlayerTestTags.NEXT_BUTTON).assertIsEnabled()

    assertEquals(1, retries.get())
  }

  @Test
  fun theEndOfTheNewestRecordingIsTold() {
    setScreen(RecordingPlayerUiState(AT_1510, nearestNotice = false, status = PlayerStatus.Ended))

    composeRule.onNodeWithTag(RecordingPlayerTestTags.ENDED).assertIsDisplayed()
  }
}
