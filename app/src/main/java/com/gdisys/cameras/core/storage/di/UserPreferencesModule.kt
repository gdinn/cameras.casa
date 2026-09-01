package com.gdisys.cameras.core.storage.di

import com.gdisys.cameras.core.storage.data.UserPreferencesRepositoryImpl
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UserPreferencesModule {

  @Binds
  @Singleton
  abstract fun bindUserPreferencesRepository(
    userPreferencesRepositoryImpl: UserPreferencesRepositoryImpl
  ): UserPreferencesRepository
}
