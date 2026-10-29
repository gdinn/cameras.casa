# Plano de ação: §6.2, nível B (fluxos herméticos com Hilt)

Implementa o §6.2 do `e2e_research.md`: testes de fluxo com **Activity, Hilt, navegação, ViewModels,
use cases e DataStore criptografado reais**, trocando só o que sai do processo (túnel WireGuard e
WebRTC/WHEP). Cada teste prepara o estado (DataStore, permissões do sistema, fakes), sobe o app
pela raiz de navegação de verdade e verifica o que o usuário veria e o que foi persistido.

Escopo: infraestrutura (runner Hilt, Orchestrator, fakes, activity de teste), tags da Home e os
cenários #1 a #12 do §6.2, mais um smoke do `MainActivity` real.

Fora do escopo: diálogos do sistema de verdade (UI Automator), decodificação dos PNGs, VPN real e
release minificado (todos do nível C), e o job de CI com GMD, que é o passo 3 da ordem do relatório
e terá plano próprio. `QrCodeScreen` continua sem teste: o QR entra pelo `savedStateHandle` (§4.3
do relatório).

---

## 1. Pontos encontrados na leitura do código

Estes pontos mudam o que o §6.2 do relatório previa:

| # | Achado | Onde | Consequência |
|---|---|---|---|
| 1 | **O `NavController` nasce dentro do `setContent` do `MainActivity`** (`rememberNavController()`), e nada o expõe | `MainActivity.kt` | Com `createAndroidComposeRule<MainActivity>()` o teste não alcança o `NavController`, então não consegue gravar o QR no `savedStateHandle` (decisão do §4.3) nem ler a pilha. **Saída:** extrair o conteúdo do `MainActivity` para um composable `CamerasAppContent(eglBase, navController)` e, nos testes, montar esse mesmo composable numa `HiltTestActivity` vazia, guardando o `NavController` criado pelo teste. O `MainActivity` real continua coberto por um smoke próprio (§4.2) |
| 2 | **O `MainActivity` trata rotação sozinho** (`configChanges="orientation\|screenSize\|..."`) e chama `enableEdgeToEdge()` | `AndroidManifest.xml`, `MainActivity.kt` | A `HiltTestActivity` precisa declarar o **mesmo** `configChanges`, o mesmo tema e chamar `enableEdgeToEdge()`. Sem isso, girar a tela recria a activity de teste, o conteúdo do `setContent` some e o cenário #7 testaria outra coisa. Também quer dizer que o #7 cobre *mudança de configuração*, não recriação |
| 3 | **O `ConfigRoute` chama `VpnService.prepare(context)` direto** para montar o `Intent` de consentimento | `feature/config/ConfigRoute.kt` | Mesmo com o `VpnRepository` falso, o diálogo que abre é o do sistema. **Decisão (diverge da recomendação do §10 do relatório):** não extrair a interface. O consentimento é estado do sistema, controlado pelo teste com `appops set <pacote> ACTIVATE_VPN allow\|ignore`, e o diálogo é interceptado com **Espresso-Intents** (`intending(...).respondWith(RESULT_OK / RESULT_CANCELED)`), que pega o `startActivityForResult` do `ActivityResultRegistry`. Cobre o caminho "pedir → aceito/negado" sem mudar produção, e ainda exercita o `prepare` real. A confirmar no `Pixel_8a` |
| 4 | **Três leitores do consentimento de VPN precisam concordar:** `VpnPermissionRepository` (estado inicial do passo), `VpnRepository.getVpnPermissionIntent()` (decisão no clique) e o `prepare` do route | `ConfigViewModel`, `ConfigRoute` | Fakes de permissão independentes geram estados impossíveis em produção (ex.: passo em `Ready` e toast "já concedida" no clique). **Decisão:** não substituir o `PermissionModule`. As permissões usam os repositórios **reais**, e o teste muda o estado do sistema (`appops` para VPN, `UiAutomation.grantRuntimePermission` para câmera). O `FakeVpnRepository.getVpnPermissionIntent()` delega ao `VpnService.prepare` real. **Revisto:** com o 1a + item 2 do `e2e_testability.md`, o consentimento de VPN tem uma fonte só (`VpnPermissionRepository.getVpnConsent()`), e o `getVpnPermissionIntent()` não existe mais. A VPN pode usar um `FakeVpnPermissionRepository` com Espresso-Intents. A câmera continua com a permissão real |
| 5 | **Câmera concedida não abre diálogo.** O contrato `RequestPermission` tem resultado síncrono quando a permissão já existe, e o `ComponentActivity` o entrega sem lançar nada | `ConfigRoute.kt` (`LaunchActivityResultOnEvent`) | O onboarding cobre `Ready → Done` sem UI Automator: o teste concede a câmera com `grantRuntimePermission` (que **não** mata o processo, ao contrário de revogar) e clica no passo. O estado "não concedida" vem do `clearPackageData` do Orchestrator, que zera as permissões runtime (a confirmar) |
| 6 | **A Home não tem nenhuma tag**, e no modo dinâmico o `CameraGridItem` não recebe `modifier` | `feature/cameras/components/*` | Tags novas em `HomeTestTags`. As dos cards e alças são aplicadas **dentro** do `CameraGridItem`, derivadas da `url` (mesma solução do `removeUrlButton` no nível A) |
| 7 | **Falha de stream só gera toast.** `connectStream` chama `showToast(STREAM_CONNECTION_ERROR)` e não há estado de erro no card | `HomeViewModel.connectStream` | O cenário #10 do relatório ("card em estado de erro") não tem o que afirmar. Vira: a falha de um stream não derruba a Home nem os outros streams. O texto do toast fica de fora (decisão "validar o estado primeiro" do relatório) |
| 8 | **`FakeWhepClient` é desnecessário.** O Dagger só resolve os bindings alcançáveis: trocando o `WhepConnectionModule`, ninguém mais pede `WhepClient`, e o `PeerConnectionFactory` nunca é criado | `core/webrtc/di/*` | Só o `StreamConnectionRepository` é substituído. O `EglBase` real continua (o `WebRtcVideoPlayer` cria o `SurfaceViewRenderer` com ele). Confirmar compilando |
| 9 | **`StreamConnectionRepository` é sem escopo no `ViewModelComponent`** (uma instância por ViewModel, por causa do `closeAll()` terminal) | `WhepConnectionModule` | O fake por ViewModel delega a um gravador `@Singleton` (`FakeStreamConnections`), que é o que o teste injeta e consulta |
| 10 | **Trabalho fora da main thread.** `ConnectVpnUseCase` e `DisconnectVpnUseCase` rodam em `Dispatchers.Default`, e o DataStore grava em `IO` | `core/vpn/domain/usecase/*`, `EncryptedPreferencesSerializer` | A sincronização do Compose Test não espera por isso. As asserções depois de ações assíncronas usam `waitUntilExactlyOneExists` / `waitUntil` com timeout, e os contadores dos fakes são atômicos. Continua sem `Thread.sleep` |
| 11 | **Os grids padrão são dinâmicos nas duas orientações** (`dynamicRows = true`) | `StreamPreferences` | O `DraggableReconfigureButton` (FAB) só existe no modo fixo, então os cenários que o usam semeiam `dynamicRows = false`. No modo dinâmico, o "Reconfigurar" aparece só depois de um overscroll de **uma tela inteira de altura** (`OverscrollReconfigure`), o que pede vários `swipeUp` |
| 12 | **O ciclo de vida da Home segue o `NavBackStackEntry`**, limitado pelo da activity | `HomeRoute` (`LifecycleEventEffect`) | `moveToState(STARTED)` pausa a Home, mas **navegar para o Config também pausa**: o túnel cai ao abrir o Config e volta ao retornar. Os cenários #8, #9 e #11 afirmam os contadores com isso em mente |
| 13 | **O `InitViewModel` fica vivo na pilha** (o `Loading` não sai ao navegar para o Config) e continua coletando as credenciais | `NavigationRoot`, `InitViewModel` | Depois de um QR válido ele tenta enviar `ToHome` sem coletor. Hoje isso não aparece na tela, mas o cenário #3 afirma que o app **continua no Config** depois de um QR válido, para pegar uma regressão aí |
| 14 | **QR inválido depois de um válido apaga as credenciais** (`updateUserPreferences(UserPreferences())` nos dois ramos de falha) | `ConfigViewModel.onQrCodeScanned` | Comportamento atual, documentado por um teste do #3 (passo do QR em `Error` e repositório vazio) |
| 15 | **Trocar o runner afeta o nível A.** Com o `HiltTestRunner`, toda a suíte roda na `HiltTestApplication` | `testInstrumentationRunner` | Os testes do nível A não são `@HiltAndroidTest` e usam a `ComponentActivity` do `ui-test-manifest`, que não é entry point, então devem continuar verdes. O `CamerasApp` é vazio, então a `HiltTestApplication` não perde nada. Confirmar no passo 3.1 |

---

## 2. Pré-requisitos

1. **O mesmo AVD do nível A** (`Pixel_8a`, API 37, imagem `default`, cold boot, animações
   desligadas). Ver o §2 do `e2e_level_a_plan.md` e o §10 do `docs/architecture.md`.
2. **Nível A verde** com `./gradlew :app:connectedDebugAndroidTest` antes de mexer no runner. Se
   falhar depois do §3.1, o problema é da infraestrutura nova.
3. **Shell da instrumentação.** Os helpers de estado do dispositivo usam
   `UiAutomation.executeShellCommand` (identidade `shell`). Conferir uma vez, à mão, que
   `adb shell appops set com.gdisys.cameras.debug ACTIVATE_VPN ignore` faz o `VpnService.prepare`
   devolver um `Intent`, e que `allow` faz devolver `null`.

---

## 3. Mudanças no build e no código de produção

### 3.1 Dependências e runner (`libs.versions.toml` + `app/build.gradle.kts`)

```kotlin
android {
  defaultConfig {
    testInstrumentationRunner = "com.gdisys.cameras.HiltTestRunner"
    testInstrumentationRunnerArguments["clearPackageData"] = "true"
  }
  testOptions {
    execution = "ANDROIDX_TEST_ORCHESTRATOR"
    animationsDisabled = true
  }
  sourceSets {
    // The fictional QR fixtures are the single source of truth: tests read the .json files from
    // the test APK's assets instead of keeping a copy.
    getByName("androidTest").assets.srcDir("$rootDir/qr-code-gen/examples")
  }
}

dependencies {
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.espresso.intents)
  androidTestImplementation(libs.hilt.android.testing)
  kspAndroidTest(libs.hilt.compiler)
  androidTestUtil(libs.androidx.test.orchestrator)
}
```

| Item | Versão | Observação |
|---|---|---|
| `androidx.test:runner` | a atual estável | Base do `HiltTestRunner` |
| `androidx.test.espresso:espresso-intents` | `espressoCore` (3.7.0) | Mesma versão do Espresso já usado |
| `com.google.dagger:hilt-android-testing` | `hilt` (2.59.2) | **Mesma** versão do Hilt |
| `androidx.test:orchestrator` | a atual estável | Conferir se o AGP 9.1 também pede `androidx.test.services:test-services` como `androidTestUtil` |

Antes de fixar: confirmar com AGP 9.1 o `kspAndroidTest` com o Kotlin embutido e o nome atual da
API de `sourceSets` (`assets.srcDir` vs. `assets.directories`). `navigation-testing` **não** entra:
o `NavController` vem do próprio teste (achado #1). `rules` (`GrantPermissionRule`) também não, já
que a câmera é concedida pelo `UiAutomation` no meio do teste (achado #5).

`app/src/androidTest/java/com/gdisys/cameras/HiltTestRunner.kt`:

```kotlin
/** Runs every instrumented test inside HiltTestApplication, so @HiltAndroidTest can swap bindings. */
class HiltTestRunner : AndroidJUnitRunner() {
  override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
    super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```

**Verificação desta etapa:** a suíte do nível A continua verde com o runner e o Orchestrator novos
(achado #15). Anotar o tempo antes e depois: com o Orchestrator, cada teste roda num processo novo.

**Resultado do §3.1 — feito como no código acima.**

- Versões: `runner` 1.7.0, `orchestrator` 1.6.1 e `test-services` 1.6.0, que entrou como
  `androidTestUtil`. O AGP 9.1 aceitou `kspAndroidTest` e `assets.srcDir` sem aviso de deprecação.
- Achado #15 confirmado: os 36 testes continuam verdes. O tempo foi de **49 s para 128 s** (task do
  Gradle, com instalação), ou de 42 s para 66 s somando os tempos dos testes.
- **Desvio:** o `CamerasAppContentTest` precisou virar `@HiltAndroidTest`. Na `HiltTestApplication`,
  uma activity `@AndroidEntryPoint` só funciona com o `HiltAndroidRule`. Com isso, o `EglBase` passa
  a ser injetado e o teste fica determinístico: afirma o Config com o `Loading` embaixo, e não mais
  "Config ou Home". O risco de subir a VPN real num dispositivo configurado deixou de existir.
- **Achado #5 confirmado em parte, no `Pixel_8a`:** o `pm clear` (o que o `clearPackageData` roda)
  revoga a permissão de câmera, mas **não** zera o app op `ACTIVATE_VPN`. Isso confirma o
  `setVpnConsent` em todo `@Before`, se o `appops` ainda for usado. Com o
  `FakeVpnPermissionRepository` (achado #4, revisto), o app op deixa de importar.
- Fora do escopo: o `lintDebug` acusou um erro novo, `ViewModelConstructorInComposable` no
  `QrCodeRouteTest` (do 1b do `e2e_testability.md`, não do §3.1). Corrigido, criando o ViewModel
  fora da composição. Voltaram a ser só os 3 erros antigos.

### 3.2 Raiz de composição compartilhada (`MainActivity.kt`)

```kotlin
/**
 * The app's composition root: theme, the shared EglBase and the navigation graph.
 *
 * Kept apart from MainActivity so the instrumented flow tests render exactly this tree with a
 * NavController they own (they need it to hand the QR result back through the savedStateHandle).
 */
@Composable
internal fun CamerasAppContent(eglBase: EglBase, navController: NavHostController) {
  CamerasTheme {
    CompositionLocalProvider(LocalEglBase provides eglBase) {
      NavigationRoot(navController = navController)
    }
  }
}
```

O `MainActivity` passa a chamar `setContent { CamerasAppContent(eglBase, rememberNavController()) }`.
Fica no mesmo arquivo de propósito: a classe gerada `MainActivityKt` já casa com o padrão
`MainActivity*.class` do `outOfScopeFilter`, então o JaCoCo e o `JacocoExclusionsTest` não mudam.
Sem efeito visual.

### 3.3 `HiltTestActivity` (source set `debug`)

Precisa estar no manifest do **app** (não no do APK de teste), como a `ComponentActivity` do
`ui-test-manifest`:

- `app/src/debug/java/com/gdisys/cameras/testing/HiltTestActivity.kt`: `@AndroidEntryPoint`,
  `ComponentActivity`, só `enableEdgeToEdge()` no `onCreate`.
- `app/src/debug/AndroidManifest.xml`: a activity com `exported="false"`, o tema
  `@style/Theme.Cameras` e o mesmo `configChanges` do `MainActivity` (achado #2), com um comentário
  dizendo que os dois precisam andar juntos.

O nome casa com `**/*Test*.*`, então fica fora do JaCoCo. Só existe no build debug.

**Resultado dos §3.2 e §3.3 — feito**, antes do §3.1 e fora da ordem dos commits do §7 (item 3 do
`e2e_testability.md`). Sem desvio no código de produção.

Além do plano:
- entrou o `CamerasAppContentTest`, um smoke na `HiltTestActivity` que ainda usa o
  `AndroidJUnitRunner`, com o `EglBase` criado no teste;
- há um comentário no manifest principal apontando para o de debug.

Verificação do §3.5 cumprida: unitários, `assembleDebug`, `assembleRelease` sem a activity no
manifest, e o app à mão abrindo no Config.

### 3.4 Tags da Home (`HomeTestTags`)

```kotlin
object HomeTestTags {
  const val EMPTY_STREAMS_RECONFIGURE_BUTTON = "home_empty_streams_reconfigure_button"
  const val LOADING = "home_loading"
  const val STREAM_GRID = "home_stream_grid"
  const val FOCUSED_STREAM = "home_focused_stream"
  const val RECONFIGURE_BUTTON = "home_reconfigure_button"
  const val OVERSCROLL_RECONFIGURE_BUTTON = "home_overscroll_reconfigure_button"
  const val NEXT_PAGE_BUTTON = "home_next_page_button"

  // Per-item tags: the URL is unique on screen (it is the grid key already).
  fun streamCard(url: String) = "home_stream_card:$url"
  fun dragHandle(url: String) = "home_drag_handle:$url"
}
```

| Arquivo | Nó | Tag |
|---|---|---|
| `HomeRoute.kt` | `LoadingScreen` | `LOADING` (o `modifier` já existe desde o nível A) |
| `HomeScreen.kt` | `ReorderableStreamGrid` (dinâmico) / container do `PagedStreamGrid` (fixo) | `STREAM_GRID` |
| `HomeScreen.kt` | `Button` do `AnimatedVisibility` | `OVERSCROLL_RECONFIGURE_BUTTON` |
| `FocusedStreamView.kt` | Container | `FOCUSED_STREAM` |
| `CameraGridItem.kt` | `VideoStreamCard` / `Icon` da alça | `streamCard(url)` / `dragHandle(url)`, aplicadas dentro do componente (achado #6) |
| `DraggableReconfigureButton.kt` | `ExtendedFloatingActionButton` | `RECONFIGURE_BUTTON` |
| `PagedStreamGrid.kt` (`PagerControls`) | `IconButton` de próxima página | `NEXT_PAGE_BUTTON` |

### 3.5 Verificação desta etapa

`./gradlew :app:testDebugUnitTest` (Konsist, `JacocoExclusionsTest`), `:app:assembleDebug`,
`:app:assembleRelease` (a `HiltTestActivity` não pode aparecer no release) e o app rodando à mão
no emulador sem mudança visível. Previews da Home inalterados.

---

## 4. Testes

### 4.1 Estrutura

```
app/src/androidTest/java/com/gdisys/cameras/
├── HiltTestRunner.kt
├── fake/
│   ├── FakeVpnRepository.kt              // + FakeVpnLifecycleController
│   ├── FakeStreamConnections.kt          // @Singleton recorder + the per-ViewModel repository
│   ├── TestVpnModule.kt                  // @TestInstallIn(replaces = [VpnModule::class])
│   └── TestWhepConnectionModule.kt       // @TestInstallIn(replaces = [WhepConnectionModule::class])
├── testing/
│   ├── ComposeTestSupport.kt             // (nível A) + waitForTag, backStackRoutes
│   ├── FlowTest.kt                       // base class: rules, injection, launchApp()
│   ├── DeviceState.kt                    // setVpnConsent(granted), grantCameraPermission()
│   ├── QrFixtures.kt                     // reads qr-code-gen/examples/*.json from the test assets
│   ├── Seed.kt                           // seedCredentials(...), seedStreams(...)
│   └── robot/ConfigRobot.kt, StreamURLsRobot.kt, HomeRobot.kt
└── flow/
    └── *FlowTest.kt                      // one class per scenario (§4.3)
```

Os fluxos ficam em `flow/` porque atravessam várias features e não espelham um pacote de `main`.
Os testes do nível A continuam onde estão.

### 4.2 Infraestrutura de teste

**Fakes** (instalados com `@TestInstallIn`, um grafo só para a suíte inteira):

| Fake | Substitui | Controle exposto ao teste |
|---|---|---|
| `FakeVpnRepository` (`@Singleton`) | `VpnRepository` (`VpnModule`) | `vpnState` (`MutableStateFlow`); `connect` vai para `CONNECTED`, ou lança `connectFailure` se definido; `disconnect` vai para `DISCONNECTED`; contadores atômicos. ~~`getVpnPermissionIntent()` delega ao `VpnService.prepare` real (achado #4)~~: o método foi removido (ver achado #4) |
| `FakeVpnLifecycleController` (`@Singleton`) | `VpnLifecycleController` (`VpnModule`) | Contadores de `start`/`stop`. O `VpnLifecycleService` nunca sobe |
| `FakeStreamConnections` (`@Singleton`) + `FakeStreamConnectionRepository` (sem escopo) | `StreamConnectionRepository` (`WhepConnectionModule`, `ViewModelComponent`) | URLs conectadas agora, histórico de `connect`/`disconnect`/`closeAll`, `failingUrls` (o `onError` é postado na main thread, como no real) |

Ficam **reais**: permissões (achados #4 e #5), storage (Orchestrator com `clearPackageData`, §6.3
do relatório), `WhepClientModule`, `WhepNetworkModule` e `WhepRtcModule` (achado #8).

> **Revisto (item 4 do `e2e_testability.md`):** o storage continua real (DataStore criptografado,
> Keystore), mas isolado pelo `TestStorageModule`, que já existe em `androidTest/testing/`. Ele dá a
> cada teste arquivos novos, e o `Seed` grava por eles normalmente. O Orchestrator continua no plano
> por causa das permissões.

> O `VpnModule` registra dois bindings, então o `TestVpnModule` fornece os dois.

> **Revisto (item 5c do `e2e_testability.md`):** o `FakeVpnRepository`, o
> `FakeVpnLifecycleController` e o `TestVpnModule` já existem, em `androidTest/testing/` (não em
> `fake/`, seguindo o `TestStorageModule`). Pelo item 5b, a Home aplica o ciclo de vida por um
> coletor único que descarta estados intermediários. Um pause e um resume que chegam durante um
> connect não geram disconnect. Os contadores dos cenários #8 e #11 valem para transições que
> esperam o túnel subir antes da próxima.

**`FlowTest`** (classe base abstrata):

```kotlin
@HiltAndroidTest
abstract class FlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
  @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()

  @Inject lateinit var eglBase: EglBase
  @Inject lateinit var vpn: FakeVpnRepository
  @Inject lateinit var streams: FakeStreamConnections
  // + the production use cases Seed.kt writes through

  lateinit var navController: NavHostController

  @Before fun injectDependencies() = hiltRule.inject()

  /** Renders the real composition root. Call it after seeding state: ViewModels are created here. */
  fun launchApp() = composeRule.setContent {
    navController = rememberNavController()
    CamerasAppContent(eglBase, navController)
  }
}
```

Como a activity sobe vazia e o conteúdo só entra no `launchApp()`, o teste semeia o DataStore
**antes** de os ViewModels existirem. O `ActivityScenario.launch` manual que o §6.3 do relatório
previa para o cenário #5 deixa de ser necessário.

**Helpers:**

- `DeviceState.setVpnConsent(granted)`: `appops set <pacote> ACTIVATE_VPN allow|ignore`, com o
  pacote vindo de `targetContext.packageName` (cobre o sufixo `.debug`). Lê a saída inteira do
  `executeShellCommand` para esperar o comando terminar. Toda classe chama no `@Before`, porque não
  é certo que o `clearPackageData` zere os app ops.
- `DeviceState.grantCameraPermission()`: `UiAutomation.grantRuntimePermission`. Revogar não existe
  aqui (mataria o processo).
- `QrFixtures.read("1_validqr_bogus_info")`: lê o `.json` de
  `InstrumentationRegistry.getInstrumentation().context.assets` (o APK de **teste**).
- `Seed.seedCredentials(fixture)` / `Seed.seedStreams(urls, portraitGrid, landscapeGrid)`: gravam
  pelos use cases de produção (`ParseUserPreferencesFromQrCodeUseCase` +
  `SaveUserPreferencesUseCase`, `SaveStreamUrlsUseCase`, `SaveGridPreferencesUseCase`), ou seja,
  pelo DataStore criptografado real.
- `ConfigRobot.injectQr(json)`: `navController.getBackStackEntry<NavigationRoute.Config>()
  .savedStateHandle[QR_CODE_RESULT_KEY] = json`, na main thread (decisão do §4.3).
- `backStackRoutes()`: a pilha como lista de rotas, para afirmar "sem destino duplicado".
- Asserções depois de trabalho assíncrono usam `waitUntilExactlyOneExists(hasTestTag(...))` ou
  `waitUntil { ... }` com timeout de 5 s (achado #10).

**Robots** (`ConfigRobot`, `StreamURLsRobot`, `HomeRobot`): o teste lê como o fluxo do usuário, e
as tags ficam num lugar só. Ex.: `config { assertStep(QR_CODE_STEP, Done); tapHome() }`,
`streamUrls { addUrl(port = "8889", name = "cam_1"); saveUrls(); back() }`,
`home { assertColumns(2); reconfigure() }`. O `assertColumns` conta os `left` distintos dos
`streamCard` visíveis.

### 4.3 Cenários

Cada linha é uma classe em `flow/`. Salvo indicação, a VPN começa **sem** consentimento, a câmera
sem permissão e o DataStore vazio.

| # | Classe | Testes | Verifica |
|---|---|---|---|
| 0 | `MainActivitySmokeTest` | `realEntryPointOpensConfigOnFirstRun` | `ActivityScenario.launch(MainActivity::class.java)` (o real, com o `EglBase` injetado nele) → os quatro passos do Config aparecem. Única classe que não usa o `launchApp()` |
| 1 | `FirstRunFlowTest` | `firstRunLandsOnConfig`, `firstRunStepsStartPending` | Pilha `[Loading, Config]`; câmera, QR, VPN e URLs em `Ready` (pelo `stateDescription` do nível A); botão diz "Navigate to Home" |
| 2 | `OnboardingFlowTest` | `completeOnboardingReachesHome` | Concede câmera → clica no passo → `Done` (achado #5); injeta a fixture 1 → QR `Done`; clica na VPN com o consentimento stubado em `RESULT_OK` → `Done`; URLs → carrega padrões → salva → volta → `Done`; Home → pilha `[Home]`, 4 `streamCard`, `vpn.connectCalls == 1`, `lifecycle.startCalls == 1`, as 4 URLs em `streams.connected` |
| 3 | `QrCodeFlowTest` | `validQrCompletesTheStep`, `qrWithMissingFieldIsRejected` (fixture 2), `qrWithWrongTypeIsRejected` (fixture 3), `qrWithUnknownShapeIsRejected` (fixture 4), `invalidQrAfterValidOneClearsCredentials` | Válido: `Done`, credenciais no repositório, **e o app continua no Config** (achado #13). Inválidos: `Error`, nada salvo, continua no Config. O último documenta o achado #14 |
| 4 | `ConfigGateFlowTest` | `homeButtonStaysWhileRequirementsAreMissing`, `backIsConsumedWhileRequirementsAreMissing`, `backIsConsumedWithoutHomeBehindEvenWhenComplete`, `deniedVpnConsentKeepsStepPending`, `grantedVpnConsentSkipsTheDialog` | Rota atual continua `Config` e a activity não fecha. Consentimento negado (`RESULT_CANCELED`) → VPN em `Ready`. Com `appops allow`, o passo já começa em `Done` e nenhum `Intent` é disparado (`Intents.assertNoUnverifiedIntents`) |
| 5 | `ReturningUserFlowTest` | `storedConfigurationOpensHome`, `credentialsWithoutUrlsShowEmptyHome`, `emptyHomeLeadsToConfig` | Semeado: pilha `[Home]`, grid com as URLs, sem passar pelo Config. Sem URLs: `EmptyStreamsScreen`, `vpn.connectCalls == 0`. O atalho abre o Config com "Back to Home" |
| 6 | `StreamUrlValidationFlowTest` | `invalidPortIsRejected`, `invalidStreamNameIsRejected`, `duplicateUrlIsRejected`, `validUrlIsPersistedOnSave`, `unsavedChangesAskBeforeLeaving` | Rejeitadas: formulário aberto, lista igual. Válida + salvar: linha nova e URL no repositório; de volta ao Config, URLs `Done`. Back com alteração pendente → diálogo de descartar; confirmar → nada persistido |
| 7 | `GridOrientationFlowTest` | `portraitUsesPortraitGrid`, `rotatingSwitchesToLandscapeGrid`, `savedGridIsAppliedOnHome` | Semeado com retrato 1×N e paisagem 3 colunas: `assertColumns(1)`, `requestedOrientation = LANDSCAPE` → `assertColumns(3)` e a ordem de paisagem. O último altera o grid pela tela de URLs e confere na Home |
| 8 | `VpnLifecycleFlowTest` | `pausingDisconnectsAndResumingReconnects`, `pausedHomeReleasesStreams` | `moveToState(STARTED)` → `disconnectCalls == 1`, `stopCalls == 1`, Home em `LOADING`; `moveToState(RESUMED)` → `connectCalls == 2`, grid de volta. Pausada, `streams.connected` fica vazio (os players saem da composição) |
| 9 | `VpnFailureFlowTest` | `failedConnectionReturnsToConfig`, `retryingFromConfigDoesNotStackDestinations` | `connectFailure` definido → Config com "Back to Home", pilha `[Home, Config]`. Voltar → nova falha → pilha ainda `[Home, Config]` |
| 10 | `StreamFailureFlowTest` | `oneFailingStreamKeepsTheOthers` | Uma URL em `failingUrls` → Home de pé, os outros cards presentes e conectados (achado #7) |
| 11 | `ReconfigureFlowTest` | `fixedGridButtonOpensConfig`, `overscrollRevealsReconfigure`, `returningToHomeDoesNotStackIt`, `reconfigureCyclesTheTunnel` | FAB (grid fixo semeado) e overscroll (grid dinâmico, achado #11) levam ao Config. "Back to Home" e o back do sistema voltam para `[Home]`, nunca `[Home, Config, Home]`. A ida e a volta fazem um `disconnect` e um novo `connect` (achado #12) |
| 12 | `ReorderStreamsFlowTest` | `draggingAStreamPersistsTheNewOrder`, `reorderingKeepsTheOtherOrientation` | Modo dinâmico: arrastar a `dragHandle` da primeira URL para depois da segunda (`performTouchInput { down; moveBy em passos; up }`) → nova ordem na tela e no `portraitOrder`; `landscapeOrder` intacto. O arraste do modo fixo (gesto no container) fica como extensão, se este se mostrar estável |

O #12 depende de gesto e tende a ser o mais instável. Fica por último, como o relatório recomenda.

Ficam de fora, como candidatos a uma segunda rodada: foco num stream (toque no card →
`FOCUSED_STREAM`, back limpa o foco), saída com back duplo na Home e a paginação do grid fixo
(`NEXT_PAGE_BUTTON` e a queda das conexões da página anterior).

### 4.4 Convenções

As do nível A continuam valendo (tag primeiro, texto só via `string(...)`, nomes camelCase, um
comportamento por teste). Mais estas:

- O estado é preparado **antes** do `launchApp()`: semeadura, `setVpnConsent`, fakes. Depois disso
  o teste só interage pela UI ou pelo ciclo de vida.
- Asserções sobre o que foi persistido leem pelos repositórios (`first()`), nunca pelo arquivo.
- Nenhum JSON de QR em mensagem de asserção, nem das fixtures fictícias (§4.4 do relatório). Comparar
  campos isolados ou usar `assertTrue(... == ...)`.
- Toasts não são verificados. O teste afirma o efeito (passo, rota, pilha, contadores).
- `@TestInstallIn` sempre. `@UninstallModules` + `@BindValue` só se um teste precisar de um
  comportamento que o fake não programa, e com um comentário dizendo por quê (cada uso recompila um
  grafo próprio).

---

## 5. Comandos

```bash
./gradlew :app:connectedDebugAndroidTest                         # níveis A e B
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.gdisys.cameras.flow
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gdisys.cameras.flow.OnboardingFlowTest
# relatório: app/build/reports/androidTests/connected/debug/index.html
```

Rodar também em pt-BR uma vez, como no nível A (§5 do `e2e_level_a_plan.md`).

---

## 6. Documentação (exigência do `CLAUDE.md`)

| Arquivo | Mudança |
|---|---|
| `docs/architecture.md` §2 e §8.2 | Dependências novas de teste, runner Hilt e Orchestrator; `HiltTestActivity` no source set `debug` |
| `docs/architecture.md` §4 | `CamerasAppContent` como raiz de composição, compartilhada pelo `MainActivity` e pelos testes |
| `docs/architecture.md` §9.2 | Fluxos cobertos. Continua aberto: diálogos do sistema reais, a Home com vídeo de verdade e o release minificado (nível C) |
| `docs/architecture.md` §10 | Nota "Instrumented flow tests": fakes e o que fica real, controle de permissões pelo shell, Espresso-Intents no consentimento, semeadura antes do `launchApp()`, `waitUntil` e não `sleep`, os comandos e o tempo da suíte |
| `docs/architecture.md` §10.1 | Ainda sem job instrumentado; os fluxos são herméticos e entram no job do passo 3 |
| `app/build.gradle.kts` (KDoc do `jacocoTestReport`) | Citar os fluxos ao lado dos testes de tela, ainda fora do JaCoCo |
| `MainActivity.kt` / manifest de debug | Comentários: por que o `CamerasAppContent` existe e por que o `configChanges` precisa casar |
| `e2e_research.md` | Marcar o §6.2 como feito, apontando para este plano, e registrar a decisão sobre o consentimento de VPN no §10 |

---

## 7. Commits sugeridos (na `feat/e2e_tests`)

1. `build: run instrumented tests with Hilt, the orchestrator and espresso-intents` (§3.1; nível A
   ainda verde).
2. `refactor: extract the composition root from MainActivity` (§3.2).
3. `test: add a Hilt test activity to the debug build` (§3.3).
4. `test: add test tags to the Home screen` (§3.4).
5. `test: add the flow test infrastructure and fakes` (§4.1, §4.2, o smoke #0 e o #1).
6. `test: cover onboarding, QR codes, the Config gate and URL validation` (#2 a #6).
7. `test: cover orientation, VPN lifecycle and failures, and reconfiguring` (#7 a #11).
8. `test: cover stream reordering` (#12).
9. `docs: document the instrumented flow tests` (§6).

---

## 8. Critérios de pronto

- [ ] `./gradlew :app:connectedDebugAndroidTest` verde no `Pixel_8a`, em `en` e em `pt-BR`.
- [ ] A suíte inteira 3x seguidas sem falha intermitente.
- [ ] `./gradlew :app:jacocoTestCoverageVerification` continua verde.
- [ ] `assembleRelease` sem a `HiltTestActivity`, e o app real sem mudança visível.
- [ ] Tempo da suíte anotado (antes e depois do Orchestrator).
- [ ] Documentação do §6 atualizada.

## 9. Riscos

| Risco | Mitigação |
|---|---|
| O `clearPackageData` não zerar a permissão de câmera ou os app ops | `setVpnConsent` em todo `@Before`. Se a câmera não for zerada, os testes que precisam dela "não concedida" passam a usar o `appops set ... CAMERA ignore`, ou ficam no nível C |
| Espresso-Intents não interceptar o `Intent` do `VpnService.prepare` | Plano B: extrair a criação do `Intent` para trás de uma interface (§4.2 do relatório) e stubar um `Intent` de teste |
| `HiltTestActivity` divergir do `MainActivity` (tema, `configChanges`, edge-to-edge) | Comentário nos dois manifests; o smoke #0 roda no `MainActivity` real |
| Orchestrator deixar a suíte lenta demais | Medir no §3.1. Se incomodar, o `StorageModule` de teste com arquivo temporário (§6.3 do relatório) e Orchestrator sem `clearPackageData`. **A costura já existe** (item 4 do `e2e_testability.md`): `userPreferencesSerializer(crypto)` / `streamPreferencesSerializer(crypto)` com `DataStoreFactory.create` |
| Gesto de arraste (#12) e overscroll (#11) instáveis | Movimentos em passos pequenos, com `advanceEventTime`; rodar 3x antes de aceitar. Se seguir instável, `@FlakyTest` documentado e fora da execução padrão |
| Corrida com `Dispatchers.Default` / `IO` | `waitUntil` com timeout em toda asserção depois de ação assíncrona (achado #10) |
| `EglBase` / `SurfaceViewRenderer` falhar na imagem ATD do futuro CI | Hoje só o AVD local. Diferença entre as imagens é achado para o plano de CI |
