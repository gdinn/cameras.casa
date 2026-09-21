import org.w3c.dom.Element
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

android {
  namespace = "com.gdisys.cameras"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.gdisys.cameras"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
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
      signingConfig = signingConfigs.getByName("release")
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
 * The patterns below are plain strings with no link to the code, so JacocoExclusionsTest resolves
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

  val debugClasses = fileTree("${layout.buildDirectory.get()}/intermediates/classes/debug/transformDebugClassesWithAsm/dirs") {
    exclude(generatedCodeFilter + outOfScopeFilter)
  }

  sourceDirectories.setFrom(files("${projectDir}/src/main/java"))
  classDirectories.setFrom(files(debugClasses))
  executionData.setFrom(fileTree(layout.buildDirectory.get()) {
    include("jacoco/testDebugUnitTest.exec")
  })

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
    val pct = "%.1f".format(100.0 * lineCovered / total)

    val bannerId = "jacoco-line-coverage-banner"
    val html = htmlIndex.readText()
    if (!html.contains(bannerId)) {
      val banner = "<div id=\"$bannerId\" style=\"background:#2e7d32;color:#fff;" +
        "padding:10px 16px;font:bold 14px/1.4 -apple-system,Arial,sans-serif;\">" +
        "Line coverage (LINE): $lineCovered/$total = $pct%</div>"
      val bodyTag = Regex("<body[^>]*>").find(html)
      if (bodyTag != null) {
        val insertAt = bodyTag.range.last + 1
        htmlIndex.writeText(html.substring(0, insertAt) + banner + html.substring(insertAt))
      }
    }
  }
}
