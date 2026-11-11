package com.gdisys.cameras.core.recordings.di

import com.gdisys.cameras.core.recordings.RecordingPlayer
import com.gdisys.cameras.core.recordings.data.ExoRecordingPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** One player per ViewModel: unscoped, so each screen gets its own and releases it. */
@Module
@InstallIn(ViewModelComponent::class)
abstract class RecordingPlayerModule {

  @Binds
  abstract fun bindRecordingPlayer(impl: ExoRecordingPlayer): RecordingPlayer
}
