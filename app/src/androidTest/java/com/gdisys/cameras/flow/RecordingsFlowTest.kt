package com.gdisys.cameras.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.gdisys.cameras.R
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.feature.cameras.components.HomeTestTags
import com.gdisys.cameras.feature.history.components.HistoryTestTags
import com.gdisys.cameras.feature.history.player.components.RecordingPlayerTestTags
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.QrFixture
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** `cam_160`, the camera the first default stream shows. */
private val STREAM = StreamDefaults.CAMERA_STREAM_URLS.first()
private const val CAMERA = "cam_160"

private const val AT_1400 = "cam_160_2026-11-10_14-00-00.mp4"
private const val AT_1510 = "cam_160_2026-11-10_15-10-00.mp4"

/** On the share, at `RECORDINGS_NOW` (2026-11-10 15:20): two finished files today, one in October. */
private val FILES = listOf(
  "$CAMERA/2026/11/10/$AT_1400",
  "$CAMERA/2026/11/10/$AT_1510",
  "$CAMERA/2026/10/31/cam_160_2026-10-31_23-45-00.mp4"
)

private val NOVEMBER: YearMonth = YearMonth.of(2026, 11)
private val OCTOBER: YearMonth = YearMonth.of(2026, 10)
private val TODAY: LocalDate = LocalDate.of(2026, 11, 10)

/**
 * An admin's way from Home to a recording and its download, over the fake share, player and
 * downloads, and the unfinished downloads Home offers. A regular user sees none of it.
 */
@HiltAndroidTest
class RecordingsFlowTest : FlowTest() {

  private fun seedUser(fixture: QrFixture) {
    seed.credentials(fixture)
    seed.streams(listOf(STREAM))
    recordingsShare.files = FILES
  }

  private fun focusStream() {
    composeRule.onNodeWithTag(HomeTestTags.streamCard(STREAM)).performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(HomeTestTags.FOCUSED_STREAM).assertIsDisplayed()
  }

  private fun routeClasses() = backStackRoutes().map { it::class }

  /** Home → focused view → History → November → today → [slot]. */
  private fun openRecordingAt(slot: LocalTime) {
    focusStream()
    composeRule.onNodeWithTag(HomeTestTags.HISTORY_BUTTON).performClick()
    composeRule.onNodeWithTag(HistoryTestTags.month(NOVEMBER)).performClick()
    composeRule.onNodeWithTag(HistoryTestTags.day(TODAY)).performClick()
    composeRule.onNodeWithTag(HistoryTestTags.SLOT_GRID).performScrollToNode(hasTestTag(HistoryTestTags.slot(slot)))
    composeRule.onNodeWithTag(HistoryTestTags.slot(slot)).performClick()
    composeRule.waitForIdle()
  }

  @Test
  fun aRegularUserHasNoHistory() {
    seedUser(QrFixture.VALID)
    launchApp()

    focusStream()

    composeRule.onNodeWithTag(HomeTestTags.HISTORY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun anAdminOpensTheHistoryOfTheFocusedCamera() {
    seedUser(QrFixture.ADMIN)
    launchApp()

    focusStream()
    composeRule.onNodeWithTag(HomeTestTags.HISTORY_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(listOf(NavigationRoute.Home::class, NavigationRoute.History::class), routeClasses())
    composeRule.onNodeWithTag(HistoryTestTags.month(NOVEMBER)).assertIsDisplayed()
    composeRule.onNodeWithTag(HistoryTestTags.month(OCTOBER)).assertIsDisplayed()

    composeRule.onNodeWithTag(HistoryTestTags.month(NOVEMBER)).performClick()
    composeRule.onNodeWithTag(HistoryTestTags.day(TODAY)).assertIsDisplayed()
  }

  @Test
  fun aTimeWithoutARecordingPlaysTheNearestOneAndSaysSo() {
    seedUser(QrFixture.ADMIN)
    launchApp()

    // 15:00 has no file: 15:10 is the nearest, ahead of 14:00.
    openRecordingAt(LocalTime.of(15, 0))

    assertEquals(
      listOf(NavigationRoute.Home::class, NavigationRoute.History::class, NavigationRoute.RecordingPlayer::class),
      routeClasses()
    )
    assertEquals(listOf(AT_1510), playback.played)
    composeRule.onNodeWithTag(RecordingPlayerTestTags.NEAREST_NOTICE).assertIsDisplayed()
  }

  @Test
  fun aTimeWithARecordingPlaysItWithoutTheNotice() {
    seedUser(QrFixture.ADMIN)
    launchApp()

    openRecordingAt(LocalTime.of(14, 0))

    assertEquals(listOf(AT_1400), playback.played)
    composeRule.onNodeWithTag(RecordingPlayerTestTags.NEAREST_NOTICE).assertDoesNotExist()
  }

  @Test
  fun thePlayerMovesToTheNextRecording() {
    seedUser(QrFixture.ADMIN)
    launchApp()
    openRecordingAt(LocalTime.of(14, 0))

    composeRule.onNodeWithTag(RecordingPlayerTestTags.NEXT_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(listOf(AT_1400, AT_1510), playback.played)
  }

  @Test
  fun thePlayerDownloadsTheRecordingOnScreen() {
    seedUser(QrFixture.ADMIN)
    launchApp()
    openRecordingAt(LocalTime.of(14, 0))

    composeRule.onNodeWithTag(RecordingPlayerTestTags.DOWNLOAD_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(listOf(AT_1400), downloads.requested.map { it.fileName })
    assertTrue(R.string.player_download_started in toasts.shown)
  }

  @Test
  fun anAdminIsOfferedTheUnfinishedDownloadsAndResumesThem() {
    seedUser(QrFixture.ADMIN)
    val unfinished = InterruptedDownload(
      "work-1",
      Recording(CAMERA, LocalDateTime.of(2026, 11, 10, 14, 0), AT_1400),
      InterruptedDownload.Reason.INTERRUPTED
    )
    downloads.interrupted.value = listOf(unfinished)
    launchApp()

    composeRule.onNodeWithTag(HomeTestTags.INTERRUPTED_DOWNLOADS_DIALOG).assertIsDisplayed()
    composeRule.onNodeWithTag(HomeTestTags.RESUME_DOWNLOADS_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(listOf(unfinished), downloads.resumed)
    composeRule.onNodeWithTag(HomeTestTags.INTERRUPTED_DOWNLOADS_DIALOG).assertDoesNotExist()
    assertTrue(R.string.home_route_downloads_resumed in toasts.shown)
  }

  @Test
  fun aRegularUserIsNotOfferedUnfinishedDownloads() {
    seedUser(QrFixture.VALID)
    downloads.interrupted.value = listOf(
      InterruptedDownload(
        "work-1",
        Recording(CAMERA, LocalDateTime.of(2026, 11, 10, 14, 0), AT_1400),
        InterruptedDownload.Reason.FAILED
      )
    )
    launchApp()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag(HomeTestTags.INTERRUPTED_DOWNLOADS_DIALOG).assertDoesNotExist()
  }
}
