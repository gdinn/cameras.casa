package com.gdisys.cameras.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.gdisys.cameras.core.storage.data.KeystoreCryptoEngine
import com.gdisys.cameras.core.storage.data.streamPreferencesSerializer
import com.gdisys.cameras.core.storage.data.userPreferencesSerializer
import com.gdisys.cameras.core.storage.di.StorageModule
import com.gdisys.cameras.core.storage.di.StreamPreferencesStore
import com.gdisys.cameras.core.storage.di.UserPreferencesStore
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.UUID
import javax.inject.Singleton

private const val TEST_USER_PREFERENCES_KEY_ALIAS = "test-gdi-sys-storage"
private const val TEST_STREAM_PREFERENCES_KEY_ALIAS = "test-gdi-sys-stream-preferences"

/** Where the current test's stores live. Injected by tests that need to inspect the files. */
class TestStoreFiles(val userPreferences: File, val streamPreferences: File)

/**
 * Replaces the production stores in every `@HiltAndroidTest` with stores built from the same
 * serializers, on files of their own and Keystore aliases kept apart from the production ones.
 *
 * Hilt creates a new `SingletonComponent` for each test, so every test gets new files and starts
 * empty, whatever ran before it. The orchestrator's `clearPackageData` wipes the cache directory
 * between tests too, so the files do not pile up.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [StorageModule::class])
object TestStorageModule {

  @Provides
  @Singleton
  fun provideTestStoreFiles(@ApplicationContext context: Context): TestStoreFiles {
    val directory = File(context.cacheDir, "test-stores/${UUID.randomUUID()}")
    return TestStoreFiles(
      userPreferences = File(directory, "user-preferences"),
      streamPreferences = File(directory, "stream-preferences")
    )
  }

  @Provides
  @Singleton
  @UserPreferencesStore
  fun provideUserPreferencesStore(files: TestStoreFiles): DataStore<UserPreferences> =
    DataStoreFactory.create(
      serializer = userPreferencesSerializer(KeystoreCryptoEngine(TEST_USER_PREFERENCES_KEY_ALIAS)),
      produceFile = { files.userPreferences }
    )

  @Provides
  @Singleton
  @StreamPreferencesStore
  fun provideStreamPreferencesStore(files: TestStoreFiles): DataStore<StreamPreferences> =
    DataStoreFactory.create(
      serializer = streamPreferencesSerializer(KeystoreCryptoEngine(TEST_STREAM_PREFERENCES_KEY_ALIAS)),
      produceFile = { files.streamPreferences }
    )
}
