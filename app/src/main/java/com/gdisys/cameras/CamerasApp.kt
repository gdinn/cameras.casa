package com.gdisys.cameras

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Also WorkManager's configuration: the default initializer is removed from the manifest, so
 * WorkManager starts on first use with Hilt's worker factory, which injects the download worker.
 */
@HiltAndroidApp
class CamerasApp : Application(), Configuration.Provider {

  @Inject
  lateinit var workerFactory: HiltWorkerFactory

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
