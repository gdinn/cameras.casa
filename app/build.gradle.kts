import org.w3c.dom.Element
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.serialization)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  id("jacoco")
}

jacoco {
  toolVersion = "0.8.12"
}

// Release signing material never lives in the repository: it comes from the environment on CI and
// from the git-ignored app/keystore.properties for a local release build. When none of it is
// available the release build type is left unsigned (see `signingConfigs` below), so
// `assembleRelease` still runs R8 on a machine that has no keystore — which is what lets CI catch
// a shrinking failure on every pull request.
val releaseSigningProperties = Properties().apply {
  val propertiesFile = project.file("keystore.properties")
  if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}

fun releaseSigningValue(propertyName: String, environmentVariable: String): String? =
  System.getenv(environmentVariable)?.takeIf { it.isNotBlank() }
    ?: releaseSigningProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigningValue("storeFile", "CAMERAS_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "CAMERAS_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "CAMERAS_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "CAMERAS_RELEASE_KEY_PASSWORD")

// The version identity of a release comes from its git tag: CI exports the tag's semantic version
// (`v1.4.2` -> `1.4.2`) as CAMERAS_VERSION_NAME, and the version code is derived from that same
// string, so the number and the name can never disagree. Deriving it here rather than in the
// workflow keeps the rule reproducible off CI: `CAMERAS_VERSION_NAME=1.4.2 ./gradlew
// :app:assembleRelease` produces byte-for-byte the versioning the tag would.
//
// Every other build — local, pull request, push to main — has no such variable and keeps the
// development identity below. Those builds are not releasable anyway: they are unsigned, and they
// all share version code 1.
val developmentVersionCode = 1
val developmentVersionName = "1.0"

/**
 * Maps `major.minor.patch` onto a single monotonically increasing integer: `major * 1_000_000 +
 * minor * 1_000 + patch`. Android requires the version code to be a strictly increasing `Int`, so
 * minor and patch are limited to three digits each, and the scheme runs out at major 2147.
 */
fun versionCodeOf(versionName: String): Int {
  val parts = versionName.split(".")
  if (parts.size != 3) {
    throw GradleException("CAMERAS_VERSION_NAME must be major.minor.patch, but was '$versionName'.")
  }
  val (major, minor, patch) = parts.map { part ->
    part.toIntOrNull()?.takeIf { it >= 0 }
      ?: throw GradleException(
        "CAMERAS_VERSION_NAME must be major.minor.patch with non-negative numbers, " +
          "but was '$versionName'."
      )
  }
  if (minor > 999 || patch > 999) {
    throw GradleException(
      "CAMERAS_VERSION_NAME '$versionName' is out of range: minor and patch must be at most 999, " +
        "otherwise the derived version code stops increasing monotonically."
    )
  }
  if (major > 2147) {
    throw GradleException(
      "CAMERAS_VERSION_NAME '$versionName' is out of range: major must be at most 2147, " +
        "otherwise the derived version code overflows Int."
    )
  }
  return major * 1_000_000 + minor * 1_000 + patch
}

val taggedVersionName = System.getenv("CAMERAS_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }

android {
  namespace = "com.gdisys.cameras"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.gdisys.cameras"
    minSdk = 26
    targetSdk = 36
    versionCode = taggedVersionName?.let(::versionCodeOf) ?: developmentVersionCode
    versionName = taggedVersionName ?: developmentVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  signingConfigs {
    // Created only when every piece of the material is present. `findByName` below then hands the
    // release build type either this config or `null`, and `null` means "unsigned APK" rather than
    // a build that cannot even be configured.
    if (
      releaseStoreFile != null &&
      releaseStorePassword != null &&
      releaseKeyAlias != null &&
      releaseKeyPassword != null
    ) {
      create("release") {
        storeFile = file(releaseStoreFile)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    // The debug build installs alongside a release build rather than colliding with it. Without
    // the suffix both variants claim `com.gdisys.cameras`, and Android then refuses to install
    // either one over the other — the signing keys differ (debug key vs. release key), so it is
    // rejected as a signature mismatch instead of treated as an update. Suffixing the debug id
    // makes them two separate apps with separate data directories, separate Keystore entries and
    // separate backup sets.
    //
    // This changes the *application id* only. The namespace stays `com.gdisys.cameras`, which is
    // what the R class, `BuildConfig` and the relative component names in AndroidManifest.xml
    // (`.MainActivity`, `.CamerasApp`, `.core.vpn.data.VpnLifecycleService`) resolve against — so
    // none of them move, and Konsist's `LayerDependencyTest` still sees one package tree.
    //
    // Anything that asserts the installed package name has to allow for the suffix rather than
    // hardcode one id: `ExampleInstrumentedTest` asserts the namespace prefix for this reason.
    // (`BuildConfig.APPLICATION_ID` would be the other way to write it, but `buildConfig` is not
    // among this module's `buildFeatures`, and a stub test is no reason to turn it on.)
    debug {
      applicationIdSuffix = ".debug"
      // Distinguishes the two in Settings > Apps and in any crash report, which matters more here
      // than usual: every non-tagged build otherwise reports the same "1.0".
      versionNameSuffix = "-debug"
    }

    release {
      // R8 in full mode (shrink + obfuscate + optimize). The keep rules live in
      // proguard-rules.pro, one commented block per reason: kotlinx.serialization (on-disk schema,
      // QR payload and the type-safe navigation routes), org.webrtc.** and com.wireguard.**
      // (JNI entry points bound by name). Hilt/Dagger, AndroidX, CameraX and ML Kit are covered by
      // their own consumer rules.
      //
      // This stack fails at *runtime* rather than at build time when a keep rule is wrong, so a
      // green `assembleRelease` is necessary but not sufficient: any change to the keep rules, to
      // a @Serializable model or to the VPN/WebRTC dependencies must be smoke-tested on a real
      // device — scan the QR code, bring the tunnel up, and play a stream.
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = signingConfigs.findByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }
  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
  testImplementation(libs.turbine)
  testImplementation(libs.mockwebserver)
  testImplementation(libs.konsist)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  // Hilt
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.hilt.lifecycle.viewmodel.compose)
  ksp(libs.hilt.compiler)

  // Secure Storage
  implementation(libs.androidx.datastore.preferences)

  // Camera X
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  // ML Kit Barcode
  implementation(libs.mlkit.barcode.scanning)

  // Wireguard
  implementation(libs.wireguard.tunnel)

  // WebRTC
  implementation(libs.stream.webrtc.android)

  // Utils
  implementation(libs.androidx.compose.navigation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.material.icons.extended)

  // Drag-and-drop of items in LazyVerticalGrid (stream reordering on Home)
  implementation(libs.reorderable)
}

// JacocoExclusionsTest reads the `outOfScopeFilter` patterns straight out of this file. Without
// declaring the build script as an input, editing a pattern leaves the test task UP-TO-DATE, so the
// guard would not run at exactly the moment it matters.
tasks.withType<Test>().configureEach {
  inputs.file("build.gradle.kts")
    .withPropertyName("buildScript")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}

/**
 * Minimum LINE coverage the unit tests have to reach, enforced by `jacocoTestCoverageVerification`.
 *
 * LINE rather than INSTRUCTION is deliberate, and it is the number this project has always talked
 * about: the HTML banner below reports LINE, and §10.1 of docs/architecture.md states the goal in
 * those terms. JaCoCo's own headline figure is instruction coverage, which counts bytecode and so
 * weighs a long expression more than a branch — a worse proxy for "is this logic exercised" on
 * Kotlin, where a single line expands to a very variable number of instructions.
 */
val minimumLineCoverage = "0.90".toBigDecimal()

// Generated code (build tooling, KSP/Hilt) — measuring coverage here never means anything.
val generatedCodeFilter = listOf(
  "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
  "**/*Test*.*", "android/**/*.*", "**/*_Hilt*.*", "**/Hilt_*.*",
  "**/*_Factory.class", "**/*_Factory\$*.class", "**/*_MembersInjector.*",
  "**/di/**", "**/*Module*.*", "dagger/hilt/**", "hilt_aggregated_deps/**"
)

// Out of plain-unit-test scope by project decision (see docs/architecture.md §9.4): Compose UI,
// bootstrap/navigation, the theme, and native/hardware integrations with no JVM shadow.
val outOfScopeFilter = listOf(
  "com/gdisys/cameras/CamerasApp*.class",
  "com/gdisys/cameras/MainActivity*.class",
  "com/gdisys/cameras/app/navigation/**",
  "com/gdisys/cameras/core/components/LoadingScreenKt*.class",
  "com/gdisys/cameras/core/components/ToastDisplayerKt*.class",
  "com/gdisys/cameras/core/vpn/data/VpnLifecycleService*.class", // Android Service
  "com/gdisys/cameras/core/vpn/data/AppTunnel*.class", // thin wrapper over the native Tunnel
  "com/gdisys/cameras/core/webrtc/data/WhepClientImpl*.class", // native WebRTC stack
  "com/gdisys/cameras/core/webrtc/data/extensions/PeerConnectionKt*.class", // likewise
  "com/gdisys/cameras/feature/cameras/HomeRouteKt*.class",
  "com/gdisys/cameras/feature/cameras/components/**",
  "com/gdisys/cameras/feature/config/ConfigRouteKt*.class",
  "com/gdisys/cameras/feature/config/components/**",
  "com/gdisys/cameras/feature/init/InitRouteKt*.class",
  "com/gdisys/cameras/feature/init/components/**",
  "com/gdisys/cameras/feature/qrcode/QrCodeRouteKt*.class",
  "com/gdisys/cameras/feature/qrcode/components/**", // QrCodeScreen (Compose) + QrCodeAnalyzer (ImageProxy/ML Kit)
  "com/gdisys/cameras/feature/streamurls/StreamURLsRouteKt*.class",
  "com/gdisys/cameras/feature/streamurls/components/**",
  "com/gdisys/cameras/ui/theme/**",
  "com/gdisys/cameras/core/storage/data/DataStoreKt*.class", // DI wiring, no logic of its own
  "com/gdisys/cameras/core/storage/data/KeystoreCryptoEngine*.class" // AndroidKeyStore, hardware-backed
)

/**
 * The compiled debug classes the two JaCoCo tasks below measure, narrowed to the unit-testable
 * scope by the two filters above.
 *
 * The report and the verification share this on purpose: a gate computed over a different set of
 * classes than the report it is read next to would be a trap, not a gate.
 */
fun coveredDebugClasses(): ConfigurableFileTree =
  fileTree("${layout.buildDirectory.get()}/intermediates/classes/debug/transformDebugClassesWithAsm/dirs") {
    exclude(generatedCodeFilter + outOfScopeFilter)
  }

/** The `.exec` file `testDebugUnitTest` writes, which is the raw input to both JaCoCo tasks. */
fun unitTestExecutionData(): ConfigurableFileTree =
  fileTree(layout.buildDirectory.get()) {
    include("jacoco/testDebugUnitTest.exec")
  }

/**
 * Coverage report for the unit tests (JVM, debug variant).
 * Usage: ./gradlew :app:jacocoTestReport
 * Output: app/build/reports/jacoco/jacocoTestReport/html/index.html
 *
 * The scope is filtered down to the logic this project expects to cover with plain JUnit (the
 * rationale is in docs/architecture.md §9.4). Left out: generated code (R, BuildConfig, Hilt/Dagger,
 * KSP factories), and the code the project decided deliberately not to cover with unit tests —
 * Compose UI (screens and composables), bootstrap (MainActivity, CamerasApp, NavigationRoot), the
 * theme, and anything backed by a native/hardware stack with no JVM shadow (native WebRTC,
 * GoBackend/WireGuard, AndroidKeyStore, Android Service).
 *
 * Compose UI is excluded **by decision**, not because it is covered elsewhere: the Compose UI Test
 * dependencies are wired, but `src/androidTest/` holds no test of its own yet. Saying it is "tested
 * via Compose UI Test" would be a claim this repository does not back up.
 *
 * The patterns above are plain strings with no link to the code, so JacocoExclusionsTest resolves
 * each one against the compiled classes and fails the build when one stops matching.
 */
tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")
  group = "verification"
  description = "Coverage report (HTML + XML) for the debug variant unit tests, restricted to the " +
    "unit-testable scope (excludes Compose UI, bootstrap, DI and native/hardware code)."

  reports {
    xml.required.set(true)
    html.required.set(true)
  }

  sourceDirectories.setFrom(files("${projectDir}/src/main/java"))
  classDirectories.setFrom(files(coveredDebugClasses()))
  executionData.setFrom(unitTestExecutionData())

  // JaCoCo lists "Lines" in the HTML table, but the headline bar and percentage at the top is
  // always Instructions, and the plugin cannot change that. Inject a banner with the LINE
  // percentage, which is the metric this project's coverage goal is stated in.
  doLast {
    val xmlFile = reports.xml.outputLocation.get().asFile
    val htmlIndex = reports.html.outputLocation.get().asFile.resolve("index.html")
    if (!xmlFile.exists() || !htmlIndex.exists()) return@doLast

    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    val report = factory.newDocumentBuilder().parse(xmlFile).documentElement

    var lineCovered = 0
    var lineMissed = 0
    val children = report.childNodes
    for (i in 0 until children.length) {
      val node = children.item(i)
      if (node is Element && node.tagName == "counter" && node.getAttribute("type") == "LINE") {
        lineCovered = node.getAttribute("covered").toInt()
        lineMissed = node.getAttribute("missed").toInt()
      }
    }

    val total = lineCovered + lineMissed
    if (total == 0) return@doLast
    val ratio = lineCovered.toDouble() / total
    val pct = "%.1f".format(100.0 * ratio)
    val minimumPct = "%.0f".format(minimumLineCoverage.toDouble() * 100)

    // Red below the gate, so the report and jacocoTestCoverageVerification never read as
    // disagreeing with each other.
    val meetsMinimum = ratio >= minimumLineCoverage.toDouble()
    val bannerId = "jacoco-line-coverage-banner"
    val html = htmlIndex.readText()
    if (!html.contains(bannerId)) {
      val background = if (meetsMinimum) "#2e7d32" else "#c62828"
      val verdict = if (meetsMinimum) "meets" else "is below"
      val banner = "<div id=\"$bannerId\" style=\"background:$background;color:#fff;" +
        "padding:10px 16px;font:bold 14px/1.4 -apple-system,Arial,sans-serif;\">" +
        "Line coverage (LINE): $lineCovered/$total = $pct% - $verdict the $minimumPct% minimum</div>"
      val bodyTag = Regex("<body[^>]*>").find(html)
      if (bodyTag != null) {
        val insertAt = bodyTag.range.last + 1
        htmlIndex.writeText(html.substring(0, insertAt) + banner + html.substring(insertAt))
      }
    }
  }
}

/**
 * The coverage gate: fails the build when LINE coverage of the unit-testable scope falls below
 * [minimumLineCoverage].
 * Usage: ./gradlew :app:jacocoTestCoverageVerification
 *
 * It lives here rather than in the CI workflow for the same reason the release version code does:
 * a rule that only exists in a workflow cannot be checked before pushing, and drifts from the
 * report it is supposed to be about. Both CI workflows just call this task, so
 * `./gradlew :app:jacocoTestCoverageVerification` locally means exactly what CI means.
 *
 * It depends on `jacocoTestReport` rather than only on `testDebugUnitTest` so that a failing run
 * still leaves the HTML and XML reports behind — the gate says a number is too low, and the report
 * next to it says which classes made it so.
 */
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
  dependsOn("jacocoTestReport")
  group = "verification"
  description = "Fails the build when LINE coverage of the unit-testable scope is below " +
    "${minimumLineCoverage.toDouble() * 100}%."

  sourceDirectories.setFrom(files("${projectDir}/src/main/java"))
  classDirectories.setFrom(files(coveredDebugClasses()))
  executionData.setFrom(unitTestExecutionData())

  violationRules {
    rule {
      element = "BUNDLE"
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = minimumLineCoverage
      }
    }
  }
}
