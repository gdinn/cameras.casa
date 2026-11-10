package com.gdisys.cameras.e2e

import android.app.Instrumentation
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gdisys.cameras.MainActivity
import com.gdisys.cameras.core.network.STREAM_URL_HOST_PREFIX
import com.gdisys.cameras.core.storage.data.DataStoreManager
import com.gdisys.cameras.core.storage.data.StreamPreferencesDataStoreManager
import com.gdisys.cameras.core.storage.data.StreamPreferencesRepositoryImpl
import com.gdisys.cameras.core.storage.data.UserPreferencesRepositoryImpl
import com.gdisys.cameras.core.storage.data.streamPreferencesDataStore
import com.gdisys.cameras.core.storage.data.userPreferencesDataStore
import com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveStreamUrlsUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveUserPreferencesUseCase
import com.gdisys.cameras.feature.cameras.components.HomeTestTags
import com.gdisys.cameras.feature.cameras.components.StreamPlaybackState
import com.gdisys.cameras.feature.cameras.components.StreamPlaybackStateKey
import com.gdisys.cameras.feature.config.components.ConfigTestTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where `pushRealQrPayload` puts the QR JSON, and where the test deletes it from. */
private const val QR_DEVICE_PATH = "/data/local/tmp/cameras-e2e-qr.json"
private const val STREAM_PATH_ARGUMENT = "streamPath"

/** Handshake over IPv6, WHEP negotiation and the first decoded frame, with room to spare. */
private const val PLAYING_TIMEOUT_MS = 90_000L

/**
 * The release smoke, automated: the real QR → the real tunnel → a stream playing. It runs on the
 * production app (`CamerasApp`, its Hilt graph, `GoBackend`, WHEP) on a physical phone, so it is
 * the only test that proves the camera is reachable through the VPN.
 *
 * The camera step is the one thing it skips: the QR JSON goes through the same
 * `ParseUserPreferencesFromQrCodeUseCase` the scanner feeds, and is saved by the production use
 * cases, so parsing and encrypted storage are covered.
 *
 * The QR carries the test peer's private key: it is never logged, never put in a failure message
 * and never passed on a command line. See the README next to this file for how to run it.
 */
@RealEnvironment
@RunWith(AndroidJUnit4::class)
class RealEnvironmentSmokeTest {

  @get:Rule
  val composeRule = createEmptyComposeRule()

  private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context: Context = ApplicationProvider.getApplicationContext()
  private var scenario: ActivityScenario<MainActivity>? = null

  @Before
  fun requireRealEnvironment() {
    assumeTrue(
      "Run with -PrealEnvironment, CAMERAS_E2E_QR_FILE and CAMERAS_E2E_STREAM_PATH",
      streamPath() != null
    )
  }

  @After
  fun cleanUp() {
    // Closing the activity pauses Home, which drops the tunnel.
    scenario?.close()
    shell("rm -f $QR_DEVICE_PATH")
  }

  @Test
  fun realQrBringsTheTunnelUpAndAStreamPlays() {
    storeRealQr()
    storeStreamUrl(STREAM_URL_HOST_PREFIX + requireNotNull(streamPath()))
    shell("appops set ${context.packageName} ACTIVATE_VPN allow")

    scenario = ActivityScenario.launch(MainActivity::class.java)

    try {
      composeRule.waitUntil(timeoutMillis = PLAYING_TIMEOUT_MS) {
        composeRule.exists(SemanticsMatcher.expectValue(StreamPlaybackStateKey, StreamPlaybackState.PLAYING))
      }
    } catch (e: ComposeTimeoutException) {
      throw AssertionError("No stream played within ${PLAYING_TIMEOUT_MS / 1000} s: ${whereItStopped()}", e)
    }
  }

  private fun storeRealQr() {
    val rawJson = shell("cat $QR_DEVICE_PATH")
    check(rawJson.isNotBlank()) { "No QR payload at $QR_DEVICE_PATH: run through pushRealQrPayload" }

    val parsed = ParseUserPreferencesFromQrCodeUseCase()(rawJson)
    // Only the exception type: kotlinx.serialization messages quote the JSON, private key included.
    val userPreferences = parsed.getOrElse { e ->
      throw AssertionError("The QR payload did not parse (${e::class.simpleName})")
    }
    checkNotNull(userPreferences) { "The QR payload parsed but is missing VPN fields" }

    val save = SaveUserPreferencesUseCase(
      UserPreferencesRepositoryImpl(DataStoreManager(context.userPreferencesDataStore))
    )
    runBlocking { save(userPreferences).getOrThrow() }
  }

  private fun storeStreamUrl(url: String) {
    val save = SaveStreamUrlsUseCase(
      StreamPreferencesRepositoryImpl(StreamPreferencesDataStoreManager(context.streamPreferencesDataStore))
    )
    runBlocking { save(listOf(url), listOf(url), listOf(url)).getOrThrow() }
  }

  /** Names the last step reached, so a failure says whether the QR, the tunnel or WHEP broke. */
  private fun whereItStopped(): String = when {
    composeRule.exists(hasTestTag(ConfigTestTags.QR_CODE_STEP)) ->
      "the app opened Config, so it did not accept the stored credentials"
    composeRule.exists(hasTestTag(HomeTestTags.LOADING)) ->
      "Home is still waiting for the tunnel: check the endpoint, IPv6 and the test peer"
    composeRule.exists(SemanticsMatcher.expectValue(StreamPlaybackStateKey, StreamPlaybackState.FAILED)) ->
      "the tunnel is up but the WHEP negotiation failed: check the stream path and the Pi"
    composeRule.exists(SemanticsMatcher.expectValue(StreamPlaybackStateKey, StreamPlaybackState.CONNECTING)) ->
      "the stream connected but no frame arrived: check that the camera is sending video"
    else -> "the app is on none of the expected screens"
  }

  private fun streamPath(): String? =
    InstrumentationRegistry.getArguments().getString(STREAM_PATH_ARGUMENT)?.takeIf { it.isNotBlank() }

  /** Runs [command] as the shell user, which can read `/data/local/tmp` and set app ops. */
  private fun shell(command: String): String =
    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
      .bufferedReader()
      .use { it.readText() }
}

/**
 * Whether a node matches. Right after the launch, before `MainActivity` sets its content, the
 * Compose test rule throws instead of finding nothing; that counts as "not yet", so the wait goes on.
 */
private fun ComposeTestRule.exists(matcher: SemanticsMatcher): Boolean = try {
  onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
} catch (e: IllegalStateException) {
  if (e.message?.startsWith("No compose hierarchies found") != true) throw e
  false
}
