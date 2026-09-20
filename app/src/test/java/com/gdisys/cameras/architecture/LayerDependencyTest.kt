package com.gdisys.cameras.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Enforcement of the dependency rule between layers.
 *
 * The project is a single Gradle module, so nothing stops a layer at compile time from importing
 * another it should not know about. These assertions play that role: they run with the unit tests
 * and fail the build whenever an arrow points outwards.
 *
 * The rule is `app`/`feature` -> `core/<area>/domain` <- `core/<area>/data`. The implementation
 * (`data`) is a detail; consumers talk to the domain abstraction, injected by `di`.
 */
class LayerDependencyTest {

  private val productionCode = Konsist.scopeFromProduction()

  @Test
  fun `features do not import the data layer`() {
    productionCode.files
      .withPackage("$APP_PACKAGE.feature..")
      .assertFalse(
        additionalMessage = "A feature may only depend on the domain layer. Inject the domain " +
          "abstraction; the Hilt module under core/<area>/di binds it to the implementation."
      ) { file -> file.imports.any { it.name.contains(".data.") } }
  }

  @Test
  fun `the domain layer does not import the data layer`() {
    domainFiles()
      .assertFalse(
        additionalMessage = "The dependency rule points inwards: data knows domain, never the " +
          "other way around."
      ) { file -> file.imports.any { it.name.contains(".data.") } }
  }

  @Test
  fun `the domain layer does not know about features`() {
    domainFiles()
      .assertFalse(
        additionalMessage = "The domain is shared. A rule that serves a single screen belongs " +
          "under feature/<screen>/logic instead."
      ) { file -> file.imports.any { it.name.startsWith("$APP_PACKAGE.feature.") } }
  }

  @Test
  fun `the domain layer does not depend on Compose`() {
    domainFiles()
      .assertFalse(
        additionalMessage = "The domain is plain Kotlin, testable on the JVM, with no UI framework."
      ) { file -> file.imports.any { it.name.startsWith("androidx.compose.") } }
  }

  @Test
  fun `a feature does not import another feature`() {
    productionCode.files
      .withPackage("$APP_PACKAGE.feature..")
      .assertFalse(
        additionalMessage = "Features are siblings: whatever two of them share moves up to core. " +
          "Wiring them together is the navigation layer's job."
      ) { file ->
        val ownFeature = file.packagee?.name.orEmpty()
          .removePrefix("$APP_PACKAGE.feature.")
          .substringBefore('.')
        file.imports.any {
          it.name.startsWith("$APP_PACKAGE.feature.") &&
            !it.name.startsWith("$APP_PACKAGE.feature.$ownFeature.")
        }
      }
  }

  private fun domainFiles() = productionCode.files.withPackage("$APP_PACKAGE.core..domain..")

  private companion object {
    const val APP_PACKAGE = "com.gdisys.cameras"
  }
}
