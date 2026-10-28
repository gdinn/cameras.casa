package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences

/*
 * The serializers behind `userPreferencesDataStore` and `streamPreferencesDataStore`, exposed so
 * instrumented tests can build a store with the same encoding on a file and Keystore alias of
 * their own (`DataStoreFactory.create`), instead of sharing the production file, which lives for
 * the whole process.
 *
 * They live apart from the `by dataStore` delegates on purpose: touching a delegate's file class
 * initializes it, and with it a `KeystoreCryptoEngine`, which does not exist on the JVM.
 */

internal fun userPreferencesSerializer(crypto: CryptoEngine) = EncryptedPreferencesSerializer(
  serializer = UserPreferences.serializer(),
  defaultValue = UserPreferences(),
  crypto = crypto
)

internal fun streamPreferencesSerializer(crypto: CryptoEngine) = EncryptedPreferencesSerializer(
  serializer = StreamPreferences.serializer(),
  defaultValue = StreamPreferences(),
  crypto = crypto
)
