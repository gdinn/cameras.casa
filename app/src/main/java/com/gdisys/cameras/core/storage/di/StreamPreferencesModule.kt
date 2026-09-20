package com.gdisys.cameras.core.storage.di

import com.gdisys.cameras.core.storage.data.StreamPreferencesRepositoryImpl
import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamPreferencesModule {

  @Binds
  @Singleton
  abstract fun bindStreamPreferencesRepository(
    streamPreferencesRepositoryImpl: StreamPreferencesRepositoryImpl
  ): StreamPreferencesRepository
}
