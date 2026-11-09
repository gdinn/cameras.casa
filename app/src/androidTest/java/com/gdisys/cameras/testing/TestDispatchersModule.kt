package com.gdisys.cameras.testing

import com.gdisys.cameras.core.coroutines.DefaultDispatcher
import com.gdisys.cameras.core.coroutines.IoDispatcher
import com.gdisys.cameras.core.coroutines.di.DispatchersModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Replaces the IO and Default dispatchers in every `@HiltAndroidTest` with the [TestDispatchers],
 * so the Compose test rule waits for the work the app moves off the main thread: the VPN use cases,
 * the DataStore reads and writes (see `TestStorageModule`) and the WHEP request.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DispatchersModule::class])
object TestDispatchersModule {

  @Provides
  @IoDispatcher
  fun provideIoDispatcher(): CoroutineDispatcher = TestDispatchers.io

  @Provides
  @DefaultDispatcher
  fun provideDefaultDispatcher(): CoroutineDispatcher = TestDispatchers.default
}
