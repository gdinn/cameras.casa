package com.gdisys.cameras.feature.streamurls

import app.cash.turbine.test
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamDefaults
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveGridPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveStreamUrlsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StreamURLsViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val getStreamPreferencesUseCase = mockk<GetStreamPreferencesUseCase>()
  private val saveStreamUrlsUseCase = mockk<SaveStreamUrlsUseCase>()
  private val saveGridPreferencesUseCase = mockk<SaveGridPreferencesUseCase>()

  private val camA = "http://[fd00:20::cafe]:8889/cam_a"
  private val camB = "http://[fd00:20::cafe]:8889/cam_b"

  private val portraitGrid = GridPreferences(columns = 1, rows = 1, dynamicRows = true)
  private val landscapeGrid = GridPreferences(columns = 2, rows = 1, dynamicRows = true)

  private val storedPreferences = StreamPreferences(
    streamUrls = listOf(camA, camB),
    // Portrait's order is reversed on purpose: saving has to preserve it.
    portraitOrder = listOf(camB, camA),
    landscapeOrder = listOf(camA, camB),
    portraitGrid = portraitGrid,
    landscapeGrid = landscapeGrid
  )

  @Before
  fun setUp() {
    coEvery { saveStreamUrlsUseCase(any(), any(), any()) } returns Result.success(Unit)
    coEvery { saveGridPreferencesUseCase(any(), any()) } returns Result.success(Unit)
  }

  private fun createViewModel(
    preferences: StreamPreferences = storedPreferences
  ): StreamURLsViewModel {
    every { getStreamPreferencesUseCase() } returns MutableStateFlow(preferences)
    return StreamURLsViewModel(
      getStreamPreferencesUseCase,
      saveStreamUrlsUseCase,
      saveGridPreferencesUseCase
    )
  }

  @Test
  fun `uiState is seeded from storage`() = runTest {
    val viewModel = createViewModel()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(listOf(camA, camB), state.streamUrls)
    assertFalse(state.isDirty)
  }

  @Test
  fun `onShowAddUrlForm opens the form and disables the add button`() = runTest {
    val viewModel = createViewModel()

    viewModel.onShowAddUrlForm()

    assertTrue(viewModel.uiState.value.isAddFormVisible)
    assertFalse(viewModel.uiState.value.isAddUrlButtonEnabled)
  }

  @Test
  fun `onShowAddUrlForm does not reset a form that is already open`() = runTest {
    val viewModel = createViewModel()

    viewModel.onShowAddUrlForm()
    viewModel.onPortChanged("8889")
    viewModel.onStreamNameChanged("cam_c")
    viewModel.onShowAddUrlForm()

    assertEquals("8889", viewModel.uiState.value.portInput)
    assertEquals("cam_c", viewModel.uiState.value.streamNameInput)
  }

  @Test
  fun `onCancelAddUrlForm closes the form and clears the inputs`() = runTest {
    val viewModel = createViewModel()

    viewModel.onShowAddUrlForm()
    viewModel.onPortChanged("8889")
    viewModel.onStreamNameChanged("cam_c")
    viewModel.onCancelAddUrlForm()

    val state = viewModel.uiState.value
    assertFalse(state.isAddFormVisible)
    assertEquals("", state.portInput)
    assertEquals("", state.streamNameInput)
    assertFalse(state.isDirty)
  }

  @Test
  fun `onAddUrlConfirmed appends the url to the canonical set and to both orders`() = runTest {
    val viewModel = createViewModel()
    val camC = "http://[fd00:20::cafe]:8889/cam_c"

    viewModel.onShowAddUrlForm()
    viewModel.onPortChanged("8889")
    viewModel.onStreamNameChanged("cam_c")
    viewModel.onAddUrlConfirmed()

    val state = viewModel.uiState.value
    assertEquals(listOf(camA, camB, camC), state.streamUrls)
    assertFalse(state.isAddFormVisible)
    assertTrue(state.isDirty)

    viewModel.onSaveUrlsRequested()
    viewModel.onSaveUrlsConfirmed()

    coVerify(exactly = 1) {
      saveStreamUrlsUseCase(
        streamUrls = listOf(camA, camB, camC),
        portraitOrder = listOf(camB, camA, camC),
        landscapeOrder = listOf(camA, camB, camC)
      )
    }
  }

  @Test
  fun `onAddUrlConfirmed with an invalid port shows a toast and keeps the form open`() = runTest {
    val viewModel = createViewModel()

    viewModel.onShowAddUrlForm()
    viewModel.onPortChanged("70000")
    viewModel.onStreamNameChanged("cam_c")

    viewModel.uiEvent.test {
      viewModel.onAddUrlConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.INVALID_PORT.resId),
        awaitItem()
      )
    }

    val state = viewModel.uiState.value
    assertEquals(listOf(camA, camB), state.streamUrls)
    assertTrue(state.isAddFormVisible)
    assertFalse(state.isDirty)
  }

  @Test
  fun `onAddUrlConfirmed with an invalid stream name shows a toast`() = runTest {
    val viewModel = createViewModel()

    viewModel.onShowAddUrlForm()
    viewModel.onPortChanged("8889")
    viewModel.onStreamNameChanged("cam c")

    viewModel.uiEvent.test {
      viewModel.onAddUrlConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.INVALID_STREAM_NAME.resId),
        awaitItem()
      )
    }

    assertEquals(listOf(camA, camB), viewModel.uiState.value.streamUrls)
  }

  @Test
  fun `onAddUrlConfirmed with a duplicate url shows a toast and does not add it`() = runTest {
    val viewModel = createViewModel()

    viewModel.onShowAddUrlForm()
    viewModel.onPortChanged("8889")
    viewModel.onStreamNameChanged("CAM_A")

    viewModel.uiEvent.test {
      viewModel.onAddUrlConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.DUPLICATE_URL.resId),
        awaitItem()
      )
    }

    assertEquals(listOf(camA, camB), viewModel.uiState.value.streamUrls)
  }

  @Test
  fun `onRemoveUrl removes the url from the canonical set and from both orders`() = runTest {
    val viewModel = createViewModel()

    viewModel.onRemoveUrl(camA)

    assertEquals(listOf(camB), viewModel.uiState.value.streamUrls)
    assertTrue(viewModel.uiState.value.isDirty)

    viewModel.onSaveUrlsRequested()
    viewModel.onSaveUrlsConfirmed()

    coVerify(exactly = 1) {
      saveStreamUrlsUseCase(
        streamUrls = listOf(camB),
        portraitOrder = listOf(camB),
        landscapeOrder = listOf(camB)
      )
    }
  }

  @Test
  fun `onLoadDefaultsRequested only opens the confirmation dialog`() = runTest {
    val viewModel = createViewModel()

    viewModel.onLoadDefaultsRequested()

    assertEquals(StreamURLsDialog.ConfirmLoadDefaults, viewModel.uiState.value.dialog)
    assertEquals(listOf(camA, camB), viewModel.uiState.value.streamUrls)
    assertFalse(viewModel.uiState.value.isDirty)
  }

  @Test
  fun `onLoadDefaultsConfirmed replaces the list and both orders in memory`() = runTest {
    val viewModel = createViewModel()

    viewModel.onLoadDefaultsRequested()
    viewModel.onLoadDefaultsConfirmed()

    assertNull(viewModel.uiState.value.dialog)
    assertEquals(StreamDefaults.CAMERA_STREAM_URLS, viewModel.uiState.value.streamUrls)
    assertTrue(viewModel.uiState.value.isDirty)
    coVerify(exactly = 0) { saveStreamUrlsUseCase(any(), any(), any()) }

    viewModel.onSaveUrlsRequested()
    viewModel.onSaveUrlsConfirmed()

    coVerify(exactly = 1) {
      saveStreamUrlsUseCase(
        streamUrls = StreamDefaults.CAMERA_STREAM_URLS,
        portraitOrder = StreamDefaults.CAMERA_STREAM_URLS,
        landscapeOrder = StreamDefaults.CAMERA_STREAM_URLS
      )
    }
  }

  @Test
  fun `onSaveUrlsRequested only opens the confirmation dialog`() = runTest {
    val viewModel = createViewModel()

    viewModel.onRemoveUrl(camA)
    viewModel.onSaveUrlsRequested()

    assertEquals(StreamURLsDialog.ConfirmSaveUrls, viewModel.uiState.value.dialog)
    coVerify(exactly = 0) { saveStreamUrlsUseCase(any(), any(), any()) }
  }

  @Test
  fun `onSaveUrlsConfirmed clears the dirty flag and shows a toast`() = runTest {
    val viewModel = createViewModel()
    coEvery { saveStreamUrlsUseCase(any(), any(), any()) } returns Result.success(Unit)

    viewModel.onRemoveUrl(camA)
    viewModel.onSaveUrlsRequested()

    viewModel.uiEvent.test {
      viewModel.onSaveUrlsConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.URLS_SAVED.resId),
        awaitItem()
      )
    }

    assertNull(viewModel.uiState.value.dialog)
    assertFalse(viewModel.uiState.value.isDirty)
  }

  @Test
  fun `onSaveUrlsConfirmed keeps the dirty flag and shows a toast when saving fails`() = runTest {
    val viewModel = createViewModel()
    coEvery { saveStreamUrlsUseCase(any(), any(), any()) } returns
      Result.failure(RuntimeException("boom"))

    viewModel.onRemoveUrl(camA)
    viewModel.onSaveUrlsRequested()

    viewModel.uiEvent.test {
      viewModel.onSaveUrlsConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.SAVE_URLS_ERROR.resId),
        awaitItem()
      )
    }

    assertTrue(viewModel.uiState.value.isDirty)
  }

  @Test
  fun `onBackRequested navigates back when there is nothing pending`() = runTest {
    val viewModel = createViewModel()

    viewModel.navigateBackEvent.test {
      viewModel.onBackRequested()

      awaitItem()
    }
    assertNull(viewModel.uiState.value.dialog)
  }

  @Test
  fun `onBackRequested asks to discard when there are pending changes`() = runTest {
    val viewModel = createViewModel()

    viewModel.onRemoveUrl(camA)

    viewModel.navigateBackEvent.test {
      viewModel.onBackRequested()

      expectNoEvents()
    }
    assertEquals(StreamURLsDialog.ConfirmDiscard, viewModel.uiState.value.dialog)
  }

  @Test
  fun `onDiscardChangesConfirmed navigates back without saving`() = runTest {
    val viewModel = createViewModel()

    viewModel.onRemoveUrl(camA)
    viewModel.onBackRequested()

    viewModel.navigateBackEvent.test {
      viewModel.onDiscardChangesConfirmed()

      awaitItem()
    }
    assertNull(viewModel.uiState.value.dialog)
    coVerify(exactly = 0) { saveStreamUrlsUseCase(any(), any(), any()) }
  }

  @Test
  fun `onDialogDismissed closes the dialog and keeps the pending changes`() = runTest {
    val viewModel = createViewModel()

    viewModel.onRemoveUrl(camA)
    viewModel.onBackRequested()
    viewModel.onDialogDismissed()

    assertNull(viewModel.uiState.value.dialog)
    assertTrue(viewModel.uiState.value.isDirty)
  }

  @Test
  fun `an empty storage yields an empty editable list`() = runTest {
    val viewModel = createViewModel(preferences = StreamPreferences())

    assertEquals(emptyList<String>(), viewModel.uiState.value.streamUrls)
    assertFalse(viewModel.uiState.value.isDirty)
  }

  // --- Section B: grid configuration ---

  @Test
  fun `grid inputs are seeded from storage`() = runTest {
    val viewModel = createViewModel()

    val state = viewModel.uiState.value
    assertEquals(GridInput(columns = "1", rows = "1", dynamicRows = true), state.portraitGridInput)
    assertEquals(GridInput(columns = "2", rows = "1", dynamicRows = true), state.landscapeGridInput)
    assertFalse(state.isDirty)
  }

  @Test
  fun `editing a grid field marks only that orientation and turns the screen dirty`() = runTest {
    val viewModel = createViewModel()

    viewModel.onGridColumnsChanged(StreamOrientation.LANDSCAPE, "3")

    val state = viewModel.uiState.value
    assertEquals("3", state.landscapeGridInput.columns)
    assertEquals("1", state.portraitGridInput.columns)
    assertTrue(state.isGridSectionDirty)
    assertFalse(state.isUrlsSectionDirty)
    assertTrue(state.isDirty)
  }

  @Test
  fun `the save grid button is disabled while a field is invalid`() = runTest {
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value.isSaveGridButtonEnabled)

    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "9")
    assertFalse(viewModel.uiState.value.isSaveGridButtonEnabled)

    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "2")
    assertTrue(viewModel.uiState.value.isSaveGridButtonEnabled)
  }

  @Test
  fun `an empty rows field only blocks saving when rows are fixed`() = runTest {
    val viewModel = createViewModel()

    viewModel.onGridRowsChanged(StreamOrientation.PORTRAIT, "")
    assertTrue(viewModel.uiState.value.isSaveGridButtonEnabled)

    viewModel.onGridDynamicRowsChanged(StreamOrientation.PORTRAIT, false)
    assertFalse(viewModel.uiState.value.isSaveGridButtonEnabled)
  }

  @Test
  fun `onSaveGridRequested only opens the confirmation dialog`() = runTest {
    val viewModel = createViewModel()

    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "2")
    viewModel.onSaveGridRequested()

    assertEquals(StreamURLsDialog.ConfirmSaveGrid, viewModel.uiState.value.dialog)
    coVerify(exactly = 0) { saveGridPreferencesUseCase(any(), any()) }
  }

  @Test
  fun `onSaveGridConfirmed persists both grids and clears the grid dirty flag`() = runTest {
    val viewModel = createViewModel()

    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "2")
    viewModel.onGridDynamicRowsChanged(StreamOrientation.LANDSCAPE, false)
    viewModel.onGridRowsChanged(StreamOrientation.LANDSCAPE, "3")
    viewModel.onSaveGridRequested()

    viewModel.uiEvent.test {
      viewModel.onSaveGridConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.GRID_SAVED.resId),
        awaitItem()
      )
    }

    coVerify(exactly = 1) {
      saveGridPreferencesUseCase(
        portraitGrid = GridPreferences(columns = 2, rows = 1, dynamicRows = true),
        landscapeGrid = GridPreferences(columns = 2, rows = 3, dynamicRows = false)
      )
    }
    assertNull(viewModel.uiState.value.dialog)
    assertFalse(viewModel.uiState.value.isDirty)
  }

  @Test
  fun `saving the grid does not touch the stream urls`() = runTest {
    val viewModel = createViewModel()

    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "2")
    viewModel.onSaveGridRequested()
    viewModel.onSaveGridConfirmed()

    coVerify(exactly = 0) { saveStreamUrlsUseCase(any(), any(), any()) }
    assertEquals(listOf(camA, camB), viewModel.uiState.value.streamUrls)
  }

  @Test
  fun `saving the grid keeps a pending urls change dirty`() = runTest {
    val viewModel = createViewModel()

    viewModel.onRemoveUrl(camA)
    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "2")
    viewModel.onSaveGridRequested()
    viewModel.onSaveGridConfirmed()

    val state = viewModel.uiState.value
    assertFalse(state.isGridSectionDirty)
    assertTrue(state.isUrlsSectionDirty)
    assertTrue(state.isDirty)
  }

  @Test
  fun `saving a grid smaller than the stream list also shows the informative toast`() = runTest {
    val viewModel = createViewModel()

    // Fixed 1x1 with two URLs registered: valid, but it does not show every stream.
    viewModel.onGridDynamicRowsChanged(StreamOrientation.PORTRAIT, false)
    viewModel.onGridRowsChanged(StreamOrientation.PORTRAIT, "1")
    viewModel.onSaveGridRequested()

    viewModel.uiEvent.test {
      viewModel.onSaveGridConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.GRID_SAVED.resId),
        awaitItem()
      )
      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.GRID_HIDES_STREAMS.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `a grid that fits every stream does not show the informative toast`() = runTest {
    val viewModel = createViewModel()

    viewModel.onGridDynamicRowsChanged(StreamOrientation.PORTRAIT, false)
    viewModel.onGridRowsChanged(StreamOrientation.PORTRAIT, "2")
    viewModel.onGridDynamicRowsChanged(StreamOrientation.LANDSCAPE, false)
    viewModel.onGridRowsChanged(StreamOrientation.LANDSCAPE, "1")
    viewModel.onSaveGridRequested()

    viewModel.uiEvent.test {
      viewModel.onSaveGridConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.GRID_SAVED.resId),
        awaitItem()
      )
      expectNoEvents()
    }
  }

  @Test
  fun `onSaveGridConfirmed keeps the dirty flag and shows a toast when saving fails`() = runTest {
    val viewModel = createViewModel()
    coEvery { saveGridPreferencesUseCase(any(), any()) } returns
      Result.failure(RuntimeException("boom"))

    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "2")
    viewModel.onSaveGridRequested()

    viewModel.uiEvent.test {
      viewModel.onSaveGridConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.SAVE_GRID_ERROR.resId),
        awaitItem()
      )
    }

    assertTrue(viewModel.uiState.value.isGridSectionDirty)
  }

  @Test
  fun `onSaveGridConfirmed with an invalid grid shows a toast and does not persist`() = runTest {
    val viewModel = createViewModel()

    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "9")
    viewModel.onSaveGridRequested()

    viewModel.uiEvent.test {
      viewModel.onSaveGridConfirmed()

      assertEquals(
        ToastUiEvent.Show(StreamURLsToastMessage.INVALID_GRID.resId),
        awaitItem()
      )
    }

    coVerify(exactly = 0) { saveGridPreferencesUseCase(any(), any()) }
    assertTrue(viewModel.uiState.value.isGridSectionDirty)
  }

  @Test
  fun `a pending grid change alone asks to discard on back`() = runTest {
    val viewModel = createViewModel()

    viewModel.onGridColumnsChanged(StreamOrientation.PORTRAIT, "2")

    viewModel.navigateBackEvent.test {
      viewModel.onBackRequested()

      expectNoEvents()
    }
    assertEquals(StreamURLsDialog.ConfirmDiscard, viewModel.uiState.value.dialog)
  }
}
