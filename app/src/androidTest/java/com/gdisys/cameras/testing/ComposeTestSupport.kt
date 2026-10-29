package com.gdisys.cameras.testing

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gdisys.cameras.ui.theme.CamerasTheme
import java.util.Locale

/** Instrumentation argument that picks the language the screen tests render in. */
private const val TEST_LOCALE_ARGUMENT = "testLocale"

/**
 * The locale passed as `-Pandroid.testInstrumentationRunnerArguments.testLocale=pt-BR`, or `null`
 * to follow the device. Unlike changing the device locale, this needs no `adb root`, so it also
 * runs on a physical device.
 */
val testLocale: Locale? =
  InstrumentationRegistry.getArguments().getString(TEST_LOCALE_ARGUMENT)?.let(Locale::forLanguageTag)

/** Renders [content] inside the app theme, the way every screen is shown in production. */
fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) =
  setContent { InTestLocale { CamerasTheme { content() } } }

/**
 * Renders [content] in [testLocale]. The Compose resources (`stringResource`) come from
 * `LocalContext`, so this swaps it for one whose resources use the test locale. The wrapper keeps
 * the activity as its base, so whatever needs the real context still reaches it.
 */
@Composable
fun InTestLocale(content: @Composable () -> Unit) {
  val locale = testLocale
  if (locale == null) {
    content()
  } else {
    val context = LocalContext.current
    val localized = remember(context, locale) { LocalizedContext(context, locale) }
    CompositionLocalProvider(
      LocalContext provides localized,
      LocalConfiguration provides localized.resources.configuration,
      content = content
    )
  }
}

/** Resolves a string in the locale the screens render in, so text assertions match either way. */
fun string(@StringRes resId: Int): String {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val locale = testLocale ?: return context.getString(resId)
  return context.localizedResources(locale).getString(resId)
}

private class LocalizedContext(base: Context, locale: Locale) : ContextWrapper(base) {
  private val localizedResources = base.localizedResources(locale)

  override fun getResources(): Resources = localizedResources
}

private fun Context.localizedResources(locale: Locale): Resources {
  val configuration = Configuration(resources.configuration).apply { setLocales(LocaleList(locale)) }
  return createConfigurationContext(configuration).resources
}
