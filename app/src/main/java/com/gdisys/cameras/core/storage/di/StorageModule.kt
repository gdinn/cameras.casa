package com.gdisys.cameras.core.storage.di

import android.content.Context
import androidx.datastore.core.DataStore
import com.gdisys.cameras.core.storage.data.streamPreferencesDataStore
import com.gdisys.cameras.core.storage.data.userPreferencesDataStore
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    @UserPreferencesStore
    fun provideDataStore(@ApplicationContext context: Context): DataStore<UserPreferences> {
        return context.userPreferencesDataStore
    }

    @Provides
    @Singleton
    @StreamPreferencesStore
    fun provideStreamPreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<StreamPreferences> {
        return context.streamPreferencesDataStore
    }
}
