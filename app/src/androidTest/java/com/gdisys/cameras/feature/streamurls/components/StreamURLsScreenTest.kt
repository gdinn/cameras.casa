package com.gdisys.cameras.feature.streamurls.components

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.feature.streamurls.GridInput
import com.gdisys.cameras.feature.streamurls.StreamURLsDialog
import com.gdisys.cameras.feature.streamurls.StreamURLsUiState
import com.gdisys.cameras.feature.streamurls.logic.MAX_GRID_DIMENSION
import com.gdisys.cameras.testing.setThemedContent
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamURLsScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun loadingShowsOnlyTheLoadingIndicator() {
    composeRule.setStreamURLsScreen(StreamURLsUiState(isLoading = true))

    composeRule.onNodeWithTag(StreamURLsTestTags.LOADING).assertIsDisplayed()
    composeRule.onNodeWithTag(StreamURLsTestTags.LIST).assertDoesNotExist()
  }

  @Test
  fun emptyListShowsNoUrlsMessage() {
    composeRule.setStreamURLsScreen(loadedState(streamUrls = emptyList()))

    composeRule.onNodeWithTag(StreamURLsTestTags.NO_URLS_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun urlsAreListedInOrder() {
    composeRule.setStreamURLsScreen(loadedState(streamUrls = listOf(FIRST_URL, SECOND_URL)))

    val firstTop = composeRule.onNodeWithTag(StreamURLsTestTags.urlRow(FIRST_URL))
      .assertIsDisplayed()
      .fetchSemanticsNode().boundsInRoot.top
    val secondTop = composeRule.onNodeWithTag(StreamURLsTestTags.urlRow(SECOND_URL))
      .assertIsDisplayed()
      .fetchSemanticsNode().boundsInRoot.top

    assertTrue("Expected $FIRST_URL above $SECOND_URL", firstTop < secondTop)
  }

  @Test
  fun removeButtonReportsItsUrl() {
    val events = composeRule.setStreamURLsScreen(
      loadedState(streamUrls = listOf(FIRST_URL, SECOND_URL))
    )

    composeRule.onNodeWithTag(StreamURLsTestTags.removeUrlButton(SECOND_URL)).performClick()

    assertEquals(listOf("remove:$SECOND_URL"), events.calls)
  }

  @Test
  fun addButtonOpensForm() {
    val events = composeRule.setStreamURLsScreen(loadedState(isAddFormVisible = false))

    composeRule.onNodeWithTag(StreamURLsTestTags.ADD_URL_BUTTON).performClick()

    assertEquals(listOf("showAddUrlForm"), events.calls)
  }

  @Test
  fun addButtonIsDisabledWhileFormIsOpen() {
    composeRule.setStreamURLsScreen(loadedState(isAddFormVisible = true))

    composeRule.onNodeWithTag(StreamURLsTestTags.ADD_URL_BUTTON).assertIsNotEnabled()
    composeRule.onNodeWithTag(StreamURLsTestTags.ADD_URL_FORM).assertIsDisplayed()
  }

  // One field per test: the state here is fixed, so a field that loses focus to another one syncs
  // back to that state and reports it as a change, which a ViewModel-backed state never causes.
  @Test
  fun portFieldReportsTypedText() {
    val events = composeRule.setStreamURLsScreen(loadedState(isAddFormVisible = true))

    composeRule.onNodeWithTag(StreamURLsTestTags.PORT_INPUT).performTextInput("8889")

    assertEquals(listOf("port:8889"), events.calls)
  }

  @Test
  fun streamNameFieldReportsTypedText() {
    val events = composeRule.setStreamURLsScreen(loadedState(isAddFormVisible = true))

    composeRule.onNodeWithTag(StreamURLsTestTags.STREAM_NAME_INPUT).performTextInput("cam_160")

    assertEquals(listOf("streamName:cam_160"), events.calls)
  }

  @Test
  fun formConfirmAndCancel() {
    val events = composeRule.setStreamURLsScreen(loadedState(isAddFormVisible = true))

    composeRule.onNodeWithTag(StreamURLsTestTags.CONFIRM_ADD_URL_BUTTON).performClick()
    composeRule.onNodeWithTag(StreamURLsTestTags.CANCEL_ADD_URL_BUTTON).performClick()

    assertEquals(listOf("addUrlConfirmed", "cancelAddUrlForm"), events.calls)
  }

  @Test
  fun loadDefaultsAndSaveUrlsRequestConfirmation() {
    val events = composeRule.setStreamURLsScreen(loadedState())

    composeRule.onNodeWithTag(StreamURLsTestTags.LOAD_DEFAULTS_BUTTON).performClick()
    composeRule.onNodeWithTag(StreamURLsTestTags.SAVE_URLS_BUTTON).performClick()

    assertEquals(listOf("loadDefaultsRequested", "saveUrlsRequested"), events.calls)
  }

  @Test
  fun eachDialogConfirmsTheRightAction() {
    val uiState = mutableStateOf(loadedState())
    val events = composeRule.setStreamURLsScreen(uiState)

    listOf(
      Triple(
        StreamURLsDialog.ConfirmLoadDefaults,
        R.string.stream_urls_dialog_load_defaults_title,
        "loadDefaultsConfirmed"
      ),
      Triple(StreamURLsDialog.ConfirmSaveUrls, R.string.stream_urls_dialog_save_title, "saveUrlsConfirmed"),
      Triple(
        StreamURLsDialog.ConfirmSaveGrid,
        R.string.stream_urls_dialog_save_grid_title,
        "saveGridConfirmed"
      ),
      Triple(
        StreamURLsDialog.ConfirmDiscard,
        R.string.stream_urls_dialog_discard_title,
        "discardChangesConfirmed"
      )
    ).forEach { (dialog, title, expectedEvent) ->
      events.calls.clear()
      uiState.value = loadedState().copy(dialog = dialog)

      composeRule.onNode(isDialog()).assert(hasAnyDescendant(hasText(string(title))))
      composeRule.onNodeWithTag(StreamURLsTestTags.DIALOG_CONFIRM_BUTTON).performClick()

      assertEquals("Confirming $dialog", listOf(expectedEvent), events.calls)
    }
  }

  @Test
  fun dialogDismissReportsDismissal() {
    val events = composeRule.setStreamURLsScreen(
      loadedState().copy(dialog = StreamURLsDialog.ConfirmSaveUrls)
    )

    composeRule.onNodeWithTag(StreamURLsTestTags.DIALOG_DISMISS_BUTTON).performClick()

    assertEquals(listOf("dialogDismissed"), events.calls)
  }

  // One field per test, for the reason given above portFieldReportsTypedText. Replacement rather
  // than input: the fields already hold a value, and where an insertion lands depends on the cursor.
  @Test
  fun gridColumnsFieldReportsOrientation() {
    val events = composeRule.setStreamURLsScreen(
      loadedState(landscapeGridInput = GridInput(columns = "2", rows = "1", dynamicRows = true))
    )

    composeRule.onListItem(StreamURLsTestTags.gridColumnsInput(StreamOrientation.LANDSCAPE))
      .performTextReplacement("3")

    assertEquals(listOf("gridColumns:LANDSCAPE:3"), events.calls)
  }

  @Test
  fun gridRowsFieldReportsOrientation() {
    val events = composeRule.setStreamURLsScreen(
      loadedState(portraitGridInput = GridInput(columns = "1", rows = "1", dynamicRows = false))
    )

    composeRule.onListItem(StreamURLsTestTags.gridRowsInput(StreamOrientation.PORTRAIT))
      .performTextReplacement("4")

    assertEquals(listOf("gridRows:PORTRAIT:4"), events.calls)
  }

  @Test
  fun rowsFieldIsDisabledInDynamicMode() {
    composeRule.setStreamURLsScreen(
      loadedState(
        portraitGridInput = GridInput(columns = "1", rows = "1", dynamicRows = true),
        landscapeGridInput = GridInput(columns = "2", rows = "1", dynamicRows = false)
      )
    )

    composeRule.onListItem(StreamURLsTestTags.gridRowsInput(StreamOrientation.PORTRAIT))
      .assertIsNotEnabled()
    composeRule.onListItem(StreamURLsTestTags.gridRowsInput(StreamOrientation.LANDSCAPE))
      .assertIsEnabled()
  }

  @Test
  fun dynamicRowsCheckboxReportsToggle() {
    val events = composeRule.setStreamURLsScreen(
      loadedState(portraitGridInput = GridInput(columns = "1", rows = "1", dynamicRows = true))
    )

    composeRule.onListItem(StreamURLsTestTags.gridDynamicRowsCheckbox(StreamOrientation.PORTRAIT))
      .performClick()

    assertEquals(listOf("gridDynamic:PORTRAIT:false"), events.calls)
  }

  @Test
  fun saveGridEnabledOnlyWhenBothOrientationsAreValid() {
    val valid = GridInput(columns = "2", rows = "1", dynamicRows = true)
    val uiState = mutableStateOf(loadedState())
    composeRule.setStreamURLsScreen(uiState)

    listOf(
      valid to true,
      valid.copy(columns = "") to false,
      valid.copy(columns = (MAX_GRID_DIMENSION + 1).toString()) to false,
      valid.copy(rows = "", dynamicRows = false) to false
    ).forEach { (portrait, expectedEnabled) ->
      uiState.value = loadedState(portraitGridInput = portrait, landscapeGridInput = valid)

      val saveGrid = composeRule.onListItem(StreamURLsTestTags.SAVE_GRID_BUTTON)
      if (expectedEnabled) saveGrid.assertIsEnabled() else saveGrid.assertIsNotEnabled()
    }
  }

  @Test
  fun backButtonReportsBack() {
    val events = composeRule.setStreamURLsScreen(loadedState())

    composeRule.onNodeWithTag(StreamURLsTestTags.BACK_BUTTON).performClick()

    assertEquals(listOf("back"), events.calls)
  }

  /**
   * The grid section sits below the URLs in a `LazyColumn`, so it may not be composed yet. Always
   * scroll to the node first, whatever the screen size.
   */
  private fun ComposeContentTestRule.onListItem(tag: String): SemanticsNodeInteraction {
    onNodeWithTag(StreamURLsTestTags.LIST).performScrollToNode(hasTestTag(tag))
    return onNodeWithTag(tag)
  }

  private fun loadedState(
    streamUrls: List<String> = listOf(FIRST_URL),
    isAddFormVisible: Boolean = false,
    portraitGridInput: GridInput = GridInput(columns = "1", rows = "1", dynamicRows = true),
    landscapeGridInput: GridInput = GridInput(columns = "2", rows = "1", dynamicRows = true)
  ) = StreamURLsUiState(
    isLoading = false,
    streamUrls = streamUrls,
    isAddFormVisible = isAddFormVisible,
    portraitGridInput = portraitGridInput,
    landscapeGridInput = landscapeGridInput
  )

  /** Records every callback StreamURLsScreen fires, in order, with its arguments. */
  private class StreamURLsScreenEvents {
    val calls = mutableListOf<String>()
  }

  private fun ComposeContentTestRule.setStreamURLsScreen(
    uiState: StreamURLsUiState
  ): StreamURLsScreenEvents = setStreamURLsScreen(mutableStateOf(uiState))

  /** Takes a [State] so a test can drive several states through one `setContent`. */
  private fun ComposeContentTestRule.setStreamURLsScreen(
    uiState: State<StreamURLsUiState>
  ): StreamURLsScreenEvents {
    val events = StreamURLsScreenEvents()
    val calls = events.calls
    setThemedContent {
      StreamURLsScreen(
        uiState = uiState.value,
        onBack = { calls += "back" },
        onShowAddUrlForm = { calls += "showAddUrlForm" },
        onPortChanged = { calls += "port:$it" },
        onStreamNameChanged = { calls += "streamName:$it" },
        onAddUrlConfirmed = { calls += "addUrlConfirmed" },
        onCancelAddUrlForm = { calls += "cancelAddUrlForm" },
        onRemoveUrl = { calls += "remove:$it" },
        onLoadDefaultsRequested = { calls += "loadDefaultsRequested" },
        onLoadDefaultsConfirmed = { calls += "loadDefaultsConfirmed" },
        onSaveUrlsRequested = { calls += "saveUrlsRequested" },
        onSaveUrlsConfirmed = { calls += "saveUrlsConfirmed" },
        onGridColumnsChanged = { orientation, value -> calls += "gridColumns:$orientation:$value" },
        onGridRowsChanged = { orientation, value -> calls += "gridRows:$orientation:$value" },
        onGridDynamicRowsChanged = { orientation, value ->
          calls += "gridDynamic:$orientation:$value"
        },
        onSaveGridRequested = { calls += "saveGridRequested" },
        onSaveGridConfirmed = { calls += "saveGridConfirmed" },
        onDiscardChangesConfirmed = { calls += "discardChangesConfirmed" },
        onDialogDismissed = { calls += "dialogDismissed" }
      )
    }
    return events
  }

  private companion object {
    const val FIRST_URL = "http://[fd00:20::cafe]:8889/cam_160"
    const val SECOND_URL = "http://[fd00:20::cafe]:8889/cam_161"
  }
}
