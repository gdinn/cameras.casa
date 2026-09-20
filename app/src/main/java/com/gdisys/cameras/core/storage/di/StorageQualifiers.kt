package com.gdisys.cameras.core.storage.di

import javax.inject.Qualifier

/** Desambigua o `DataStore<UserPreferences>` (credenciais de VPN) no grafo do Hilt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferencesStore

/** Desambigua o `DataStore<StreamPreferences>` (URLs e grid) no grafo do Hilt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamPreferencesStore
