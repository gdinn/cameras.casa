package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/*
 * The serializers behind `userPreferencesDataStore` and `streamPreferencesDataStore`, exposed so
 * instrumented tests can build a store with the same encoding on a file and Keystore alias of
 * their own (`DataStoreFactory.create`), instead of sharing the production file, which lives for
 * the whole process.
 *
 * They live apart from the `by dataStore` delegates on purpose: touching a delegate's file class
 * initializes it, and with it a `KeystoreCryptoEngine`, which does not exist on the JVM.
 *
 * The `by dataStore` delegates take the default `ioDispatcher`; tests pass their own.
 */

internal fun userPreferencesSerializer(
  crypto: CryptoEngine,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) = EncryptedPreferencesSerializer(
  serializer = UserPreferences.serializer(),
  defaultValue = UserPreferences(),
  crypto = crypto,
  ioDispatcher = ioDispatcher
)

internal fun streamPreferencesSerializer(
  crypto: CryptoEngine,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) = EncryptedPreferencesSerializer(
  serializer = StreamPreferences.serializer(),
  defaultValue = StreamPreferences(),
  crypto = crypto,
  ioDispatcher = ioDispatcher
)
