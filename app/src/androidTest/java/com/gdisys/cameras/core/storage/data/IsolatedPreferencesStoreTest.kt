package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the storage seam the flow tests will use: a store built from [userPreferencesSerializer]
 * on a file and Keystore alias of its own starts empty, encrypts with the real Keystore, and never
 * touches the production file.
 */
@RunWith(AndroidJUnit4::class)
class IsolatedPreferencesStoreTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val id = UUID.randomUUID().toString()
  private val keyAlias = "test-user-preferences-$id"
  private val file = File(context.cacheDir, "test-user-preferences-$id")
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  @After
  fun cleanUp() {
    scope.cancel()
    file.delete()
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
  }

  @Test
  fun storeOnItsOwnFileAndAliasIsEncryptedAndLeavesProductionAlone() = runBlocking {
    val productionFile = context.dataStoreFile(USER_PREFERENCES_FILE_NAME)
    val productionBefore = productionFile.takeIf { it.exists() }?.readBytes()
    val store = DataStoreFactory.create(
      serializer = userPreferencesSerializer(KeystoreCryptoEngine(keyAlias)),
      scope = scope,
      produceFile = { file }
    )
    val preferences = UserPreferences(vpnConfigTokens = VpnConfigTokens(iPrk = "private-key"))

    assertEquals(UserPreferences(), store.data.first())
    store.updateData { preferences }

    assertEquals(preferences, store.data.first())
    assertFalse(file.readText().contains("private-key"))
    assertArrayEquals(productionBefore, productionFile.takeIf { it.exists() }?.readBytes())
  }
}
