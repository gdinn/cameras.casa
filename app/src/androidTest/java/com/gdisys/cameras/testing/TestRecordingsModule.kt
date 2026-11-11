package com.gdisys.cameras.testing

import com.gdisys.cameras.core.recordings.data.RecordingsRepositoryImpl
import com.gdisys.cameras.core.recordings.data.RecordingsShare
import com.gdisys.cameras.core.recordings.di.RecordingsModule
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Singleton

/**
 * "Now" for every `@HiltAndroidTest`: 2026-11-10 15:20. The file that starts at 15:15 is still
 * being written, and anything after today is in the future.
 */
val RECORDINGS_NOW: LocalDateTime = LocalDateTime.of(2026, 11, 10, 15, 20)

/**
 * Replaces the Samba share with [FakeRecordingsShare] in every `@HiltAndroidTest`, under the
 * production repository, and fixes the clock at [RECORDINGS_NOW] so which file counts as finished
 * does not depend on when the test runs.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RecordingsModule::class])
abstract class TestRecordingsModule {

  @Binds
  @Singleton
  abstract fun bindRecordingsRepository(impl: RecordingsRepositoryImpl): RecordingsRepository

  @Binds
  abstract fun bindRecordingsShare(fake: FakeRecordingsShare): RecordingsShare

  companion object {
    @Provides
    fun provideClock(): Clock {
      val zone = ZoneId.systemDefault()
      return Clock.fixed(RECORDINGS_NOW.atZone(zone).toInstant(), zone)
    }
  }
}
