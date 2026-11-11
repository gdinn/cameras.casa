package com.gdisys.cameras.core.recordings.di

import com.gdisys.cameras.core.recordings.data.RecordingsRepositoryImpl
import com.gdisys.cameras.core.recordings.data.RecordingsShare
import com.gdisys.cameras.core.recordings.data.SmbjRecordingsShare
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingsModule {

  @Binds
  @Singleton
  abstract fun bindRecordingsRepository(impl: RecordingsRepositoryImpl): RecordingsRepository

  @Binds
  abstract fun bindRecordingsShare(impl: SmbjRecordingsShare): RecordingsShare

  companion object {
    /** Decides which file is still being written and hides what lies in the future. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
  }
}
