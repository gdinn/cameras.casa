package com.gdisys.cameras.testing

import com.gdisys.cameras.core.recordings.RecordingPlayer
import com.gdisys.cameras.core.recordings.di.RecordingPlayerModule
import dagger.Binds
import dagger.Module
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.testing.TestInstallIn

/**
 * Replaces ExoPlayer with [FakeRecordingPlayer] in every `@HiltAndroidTest`: the real player would
 * open the file on the Samba share. Unscoped, one per ViewModel, like the real binding.
 */
@Module
@TestInstallIn(components = [ViewModelComponent::class], replaces = [RecordingPlayerModule::class])
abstract class TestRecordingPlayerModule {

  @Binds
  abstract fun bindRecordingPlayer(fake: FakeRecordingPlayer): RecordingPlayer
}
