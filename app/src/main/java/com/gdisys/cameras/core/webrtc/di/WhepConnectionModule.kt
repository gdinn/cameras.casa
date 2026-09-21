package com.gdisys.cameras.core.webrtc.di

import com.gdisys.cameras.core.webrtc.StreamConnectionRepository
import com.gdisys.cameras.core.webrtc.data.WhepConnectionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Binds the stream connection contract to its WHEP implementation.
 *
 * Installed in [ViewModelComponent] because [WhepConnectionManager] depends on `WhepClient`, which
 * is only available there. Deliberately unscoped: `closeAll()` cancels the manager's coroutine
 * scope for good, so each ViewModel must own its own instance — a shared one would be dead for
 * every screen opened after the first was destroyed.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class WhepConnectionModule {

  @Binds
  abstract fun bindStreamConnectionRepository(
    impl: WhepConnectionManager
  ): StreamConnectionRepository
}
