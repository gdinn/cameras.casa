package com.gdisys.cameras.core.coroutines.di

import com.gdisys.cameras.core.coroutines.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Kept apart from [DispatchersModule], which instrumented tests replace: the scope stays on the main
 * thread, which the Compose test rule already waits for.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

  @Provides
  @Singleton
  @ApplicationScope
  fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
