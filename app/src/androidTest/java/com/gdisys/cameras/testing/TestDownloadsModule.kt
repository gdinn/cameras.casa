package com.gdisys.cameras.testing

import com.gdisys.cameras.core.downloads.data.DeviceDownloadOutputs
import com.gdisys.cameras.core.downloads.data.DownloadOutputs
import com.gdisys.cameras.core.downloads.di.DownloadsModule
import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/**
 * Replaces the downloads in every `@HiltAndroidTest` with [FakeDownloadRepository]. WorkManager is
 * started by `CamerasApp`'s configuration, which `HiltTestApplication` does not have, so the real
 * repository would fail as soon as Home asked it for interrupted downloads.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DownloadsModule::class])
abstract class TestDownloadsModule {

  @Binds
  abstract fun bindDownloadRepository(fake: FakeDownloadRepository): DownloadRepository

  @Binds
  abstract fun bindDownloadOutputs(impl: DeviceDownloadOutputs): DownloadOutputs
}
