package com.gdisys.cameras.testing

import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveGridPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveStreamUrlsUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveUserPreferencesUseCase
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Writes the state a flow test starts from through the production use cases, so it lands in the
 * encrypted DataStore exactly as the app would write it. Call it before `launchApp()`.
 */
class Seed @Inject constructor(
  private val parseUserPreferencesFromQrCode: ParseUserPreferencesFromQrCodeUseCase,
  private val saveUserPreferences: SaveUserPreferencesUseCase,
  private val saveStreamUrls: SaveStreamUrlsUseCase,
  private val saveGridPreferences: SaveGridPreferencesUseCase
) {

  /** Stores the credentials of a valid [fixture], as a scanned QR would. */
  fun credentials(fixture: QrFixture = QrFixture.VALID) {
    val userPreferences = checkNotNull(parseUserPreferencesFromQrCode(fixture.read()).getOrThrow()) {
      "$fixture does not hold valid credentials"
    }
    runBlocking { saveUserPreferences(userPreferences).getOrThrow() }
  }

  /**
   * Stores [urls], their order in each orientation, and the grids. Both orders default to [urls].
   * The grids default to the app's, which are dynamic: scenarios that need the fixed grid (and its
   * "Reconfigure" button) pass `dynamicRows = false`.
   */
  fun streams(
    urls: List<String>,
    portraitGrid: GridPreferences = StreamPreferences().portraitGrid,
    landscapeGrid: GridPreferences = StreamPreferences().landscapeGrid,
    portraitOrder: List<String> = urls,
    landscapeOrder: List<String> = urls
  ) {
    runBlocking {
      saveStreamUrls(urls, portraitOrder, landscapeOrder).getOrThrow()
      saveGridPreferences(portraitGrid, landscapeGrid).getOrThrow()
    }
  }
}
