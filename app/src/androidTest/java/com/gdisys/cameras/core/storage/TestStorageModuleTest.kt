package com.gdisys.cameras.core.storage

import androidx.datastore.dataStoreFile
import androidx.test.platform.app.InstrumentationRegistry
import com.gdisys.cameras.core.storage.data.STREAM_PREFERENCES_FILE_NAME
import com.gdisys.cameras.core.storage.data.USER_PREFERENCES_FILE_NAME
import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import com.gdisys.cameras.testing.TestStoreFiles
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Proves `TestStorageModule` is what the production repositories write to in a Hilt test. */
@HiltAndroidTest
class TestStorageModuleTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var userPreferencesRepository: UserPreferencesRepository

  @Inject
  lateinit var streamPreferencesRepository: StreamPreferencesRepository

  @Inject
  lateinit var files: TestStoreFiles

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun injectDependencies() {
    hiltRule.inject()
  }

  @Test
  fun repositoriesStartEmptyAndWriteToTheTestFilesOnly() = runBlocking {
    val userPreferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))
    val streamUrls = listOf("http://[fd00:20::cafe]:8889/cam_1")

    assertEquals(UserPreferences(), userPreferencesRepository.userPreferences.first())
    assertEquals(StreamPreferences().streamUrls, streamPreferencesRepository.streamPreferences.first().streamUrls)

    userPreferencesRepository.updateUserPreferences(userPreferences)
    streamPreferencesRepository.updateStreamPreferences { it.copy(streamUrls = streamUrls) }

    assertEquals(userPreferences, userPreferencesRepository.userPreferences.first())
    assertEquals(streamUrls, streamPreferencesRepository.streamPreferences.first().streamUrls)
    assertTrue(files.userPreferences.exists())
    assertTrue(files.streamPreferences.exists())
    assertFalse(context.dataStoreFile(USER_PREFERENCES_FILE_NAME).exists())
    assertFalse(context.dataStoreFile(STREAM_PREFERENCES_FILE_NAME).exists())
  }
}
