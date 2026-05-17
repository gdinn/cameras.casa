package com.gdisys.cameras.core.storage.di

import android.content.Context
import androidx.datastore.core.DataStore
import com.gdisys.cameras.core.storage.UserPreferences
import com.gdisys.cameras.core.storage.dataStore
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
    fun provideDataStore(@ApplicationContext context: Context): DataStore<UserPreferences> {
        return context.dataStore
    }
}
