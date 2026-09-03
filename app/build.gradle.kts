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
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
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
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.1"
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
}

/**
 * Gera relatório de cobertura dos testes unitários (JVM, variante debug).
 * Uso: ./gradlew :app:jacocoTestReport
 * Saída: app/build/reports/jacoco/jacocoTestReport/html/index.html
 *
 * O escopo é filtrado para refletir só a lógica que se espera testar unitariamente
 * (ver relatorio_tested_expandido.md): fica de fora código gerado (R, BuildConfig, Hilt/Dagger,
 * factories do KSP) e código que o projeto decidiu deliberadamente não cobrir com JUnit puro —
 * Compose UI (telas/composables, testadas via Compose UI Test), bootstrap (MainActivity,
 * CamerasApp, NavigationRoot), tema, e o que depende de stack nativa/hardware sem shadow
 * disponível em JVM (WebRTC nativo, GoBackend/WireGuard, AndroidKeyStore, Android Service).
 */
tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")
  group = "verification"
  description = "Gera relatório de cobertura (HTML + XML) dos testes unitários da variante debug, " +
    "restrito ao escopo unit-testável (exclui Compose UI, bootstrap, DI e código nativo/hardware)."

  reports {
    xml.required.set(true)
    html.required.set(true)
  }

  // Código gerado (build tooling, KSP/Hilt) — nunca faz sentido medir cobertura aqui.
  val generatedCodeFilter = listOf(
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*Test*.*", "android/**/*.*", "**/*_Hilt*.*", "**/Hilt_*.*",
    "**/*_Factory.class", "**/*_Factory\$*.class", "**/*_MembersInjector.*",
    "**/di/**", "**/*Module*.*", "dagger/hilt/**", "hilt_aggregated_deps/**"
  )

  // Fora de escopo de teste unitário puro por decisão do projeto (relatorio_tested_expandido.md):
  // Compose UI, bootstrap/navegação, tema, e integrações nativas/hardware sem shadow em JVM.
  val outOfScopeFilter = listOf(
    "com/gdisys/cameras/CamerasApp*.class",
    "com/gdisys/cameras/MainActivity*.class",
    "com/gdisys/cameras/app/navigation/**",
    "com/gdisys/cameras/core/components/QrCodeRouteKt*.class",
    "com/gdisys/cameras/core/components/QrCodeScreenKt*.class",
    "com/gdisys/cameras/core/components/LoadingStorageScreenKt*.class",
    "com/gdisys/cameras/core/components/ToastDisplayerKt*.class",
    "com/gdisys/cameras/core/components/ComposableSingletons*.class",
    "com/gdisys/cameras/core/utils/QrCodeAnalyzer*.class", // ImageProxy/ML Kit
    "com/gdisys/cameras/core/vpn/data/VpnLifecycleService*.class", // Android Service
    "com/gdisys/cameras/core/vpn/data/AppTunnel*.class", // wrapper fino sobre Tunnel nativo
    "com/gdisys/cameras/core/webrtc/data/WhepClientImpl*.class", // stack WebRTC nativa
    "com/gdisys/cameras/core/webrtc/data/extensions/PeerConnectionKt*.class", // idem
    "com/gdisys/cameras/feature/cameras/HomeRouteKt*.class",
    "com/gdisys/cameras/feature/cameras/components/**",
    "com/gdisys/cameras/feature/config/ConfigRouteKt*.class",
    "com/gdisys/cameras/feature/config/components/**",
    "com/gdisys/cameras/feature/init/InitRouteKt*.class",
    "com/gdisys/cameras/feature/init/components/**",
    "com/gdisys/cameras/ui/theme/**",
    "com/gdisys/cameras/core/storage/data/DataStoreKt*.class", // fiação de DI, sem lógica própria
    "com/gdisys/cameras/core/storage/data/Crypto.class", // AndroidKeyStore, hardware-backed
    "com/gdisys/cameras/core/storage/data/Crypto\$*.class"
  )

  val debugClasses = fileTree("${layout.buildDirectory.get()}/intermediates/classes/debug/transformDebugClassesWithAsm/dirs") {
    exclude(generatedCodeFilter + outOfScopeFilter)
  }

  sourceDirectories.setFrom(files("${projectDir}/src/main/java"))
  classDirectories.setFrom(files(debugClasses))
  executionData.setFrom(fileTree(layout.buildDirectory.get()) {
    include("jacoco/testDebugUnitTest.exec")
  })

  // O JaCoCo lista "Lines" na tabela HTML, mas a barra/percentual de destaque no topo é sempre
  // Instructions (não configurável pelo plugin). Injeta um banner com a % de LINE, que é a
  // métrica usada para a meta de cobertura do projeto.
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
        "Cobertura de linhas (LINE): $lineCovered/$total = $pct%</div>"
      val bodyTag = Regex("<body[^>]*>").find(html)
      if (bodyTag != null) {
        val insertAt = bodyTag.range.last + 1
        htmlIndex.writeText(html.substring(0, insertAt) + banner + html.substring(insertAt))
      }
    }
  }
}
