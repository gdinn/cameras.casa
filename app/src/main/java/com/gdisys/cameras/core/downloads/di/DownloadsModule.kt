package com.gdisys.cameras.core.downloads.di

import android.content.Context
import androidx.work.WorkManager
import com.gdisys.cameras.core.downloads.data.DeviceDownloadOutputs
import com.gdisys.cameras.core.downloads.data.DownloadOutputs
import com.gdisys.cameras.core.downloads.data.WorkManagerDownloadRepository
import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadsModule {

  @Binds
  abstract fun bindDownloadRepository(impl: WorkManagerDownloadRepository): DownloadRepository

  @Binds
  abstract fun bindDownloadOutputs(impl: DeviceDownloadOutputs): DownloadOutputs

  companion object {
    /** Started on demand with `CamerasApp`'s configuration (Hilt's worker factory). */
    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
  }
}
