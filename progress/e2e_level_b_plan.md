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

**Resultado dos fakes de permissão e de streams — feito.** Eles ficam em `androidTest/testing/`,
seguindo os do item 5c.

- `FakeVpnPermissionRepository` + `TestPermissionModule`: o módulo substitui o `PermissionModule`
  inteiro, recria o binding real da câmera e troca só o consentimento de VPN.
  - Com `granted = false`, o fake devolve `VpnConsent.Required` com um Intent de ação própria
    (`CONSENT_ACTION`), que nenhum app resolve. Um teste que esquecer o stub falha, em vez de abrir
    o diálogo do sistema.
  - `stubConsentDialog(accept)` usa `intending(...).respondWithFunction`. **Desvio:** aceitar também
    marca `granted = true`, para que um `ConfigViewModel` criado depois (cenários #5 e #11) leia o
    consentimento como o `VpnService.prepare` leria depois do diálogo real.
  - Os testes usam o `IntentsRule` do `espresso-intents`.
- `FakeStreamConnections` + `FakeStreamConnectionRepository` + `TestStreamConnectionModule`, como
  previsto. Os eventos são um `sealed interface StreamEvent` (`Connect`, `Disconnect`, `CloseAll`).
  Uma URL em `failingUrls` não abre, e reconectar uma URL aberta que passou a falhar a fecha, como
  a substituição do real.
- **Achado #8 confirmado:** o Hilt compilou trocando só o `WhepConnectionModule`.
- O resultado stubado pelo Espresso-Intents chega pelo `ActivityResultRegistry`, que é o caminho do
  `ConfigRoute`. O `FakeVpnPermissionRepositoryTest` cobre isso.
- Suíte: **63 testes verdes** no `Pixel_8a` (56 + 3 do `FakeVpnPermissionRepositoryTest` + 4 do
  `FakeStreamConnectionsTest`), em 4 min 14 s com a compilação.

**Resultado da `FlowTest` e dos helpers — feito.** Suíte com **68 testes verdes** no `Pixel_8a`
(4 min 34 s com a compilação). O Hilt injeta os campos herdados da base, e credenciais com streams
semeados abrem direto na Home, com a pilha `[Home]`.

- `FlowTest` como no esboço acima, mais o `IntentsRule` (`order = 1`), para o
  `stubConsentDialog`. O `launchApp()` envolve o conteúdo no `InTestLocale`, para o `string()`
  bater com a tela. Subclasses levam o `@HiltAndroidTest`, e a base não leva.
- **Desvios:**
  - O `injectQr` e o `backStackRoutes()` ficam na `FlowTest`, não no `ConfigRobot`.
    O `backStackRoutes()` devolve objetos `NavigationRoute`, e não strings.
  - Leitores `storedUserPreferences()` e `storedStreamPreferences()` para as asserções do que foi
    persistido.
  - O `DeviceState` ficou só com o `grantCameraPermission()`, porque o consentimento de VPN é do
    fake. A rotação entra com o cenário #7.
  - O `Seed` é uma classe injetada (`seed.credentials()`, `seed.streams()`).
  - O `QrFixture` é um enum dos quatro arquivos.
- O `CamerasAppContentTest` passou a estender a `FlowTest`. O `FlowTestSupportTest` (5 testes)
  cobre os helpers.
- **Os robots ficam para os cenários:** cada um nasce com o primeiro cenário que precisar dele, para
  não criar API sem uso.

**Resultado dos cenários #0, #1 e #3 — feito.** Suíte com **75 testes verdes** no `Pixel_8a`
(4 min 46 s com a compilação). O achado #13 continua sem regressão: depois de um QR válido, o app
fica no Config.

- #0 `MainActivitySmokeTest`, #1 `FirstRunFlowTest` e #3 `QrCodeFlowTest` em `flow/`, como na
  tabela do §4.3.
- **Desvio:** o `CamerasAppContentTest` foi removido. O `firstRunLandsOnConfig` do #1 é o mesmo
  teste.
- `ConfigRobot` com `assertStep`, `assertEveryStep` e `assertHomeButtonText`, e o enum
  `ConfigStep` dos quatro passos. O `hasStateDescription` saiu do `ConfigScreenTest` para o
  `ComposeTestSupport`, compartilhado.
- O texto dos toasts continua fora, como decidido.

**Resultado dos cenários #4 e #2 — feito.** A suíte completa rodou 81 testes, com 80 verdes no `Pixel_8a`.
A falha (`grantedVpnConsentSkipsTheDialog`) foi corrigida (ver abaixo), e a classe rodou de novo
sozinha: 5 de 5. O achado #5 está confirmado: a câmera já concedida devolve o resultado sem
diálogo.

- #4 `ConfigGateFlowTest`, com os 5 testes da tabela.
  - O back passa pelo `OnBackPressedDispatcher` da activity (`FlowTest.pressBack()`), e "a activity
    não fecha" vira `!activity.isFinishing`.
  - No `backIsConsumedWithoutHomeBehindEvenWhenComplete`, as credenciais entram pelo QR depois do
    `launchApp()`: semeadas, o gate de inicialização iria direto para a Home.
  - **Desvio:** o consentimento vem do `FakeVpnPermissionRepository`, não do `appops`. No teste
    negado, o `intended(hasAction(CONSENT_ACTION))` prova que o diálogo foi pedido.
  - **Desvio:** no `grantedVpnConsentSkipsTheDialog`, o `assertNoUnverifiedIntents` do plano falhou.
    O `IntentsRule` começa antes da activity e grava o Intent que abre a `HiltTestActivity`. O teste
    passou a afirmar `intended(hasAction(CONSENT_ACTION), times(0))`.
- #2 `OnboardingFlowTest`:
  - A câmera é concedida **depois** do `launchApp()`. Concedida antes, o passo já começaria em
    `Done`, e o clique não passaria pelo pedido de permissão.
  - **Desvio:** a Home tem grid dinâmico e preguiçoso (1 coluna em retrato), então nem os 4 cards
    estão compostos ao mesmo tempo. O `HomeRobot.assertShowsStreams` rola até cada card. Em vez de
    "as 4 URLs em `streams.connected`", o teste afirma que cada URL teve um `Connect` em
    `streams.events`, porque os cards que saem da tela soltam a conexão.
- Robots: `ConfigRobot` ganhou `tapStep` e `tapHome`. Entraram o `StreamURLsRobot` (`loadDefaults`,
  `saveUrls`, `back`, que rolam a `LazyColumn` até os botões) e o `HomeRobot`.

**Resultado dos cenários #5 e #6 — feito.** O pacote `flow` passou inteiro no `Pixel_8a`: 22 de 22,
em 1 min 49 s. A suíte completa não rodou de novo, porque só os robots e o `flow/` mudaram.

- #5 `ReturningUserFlowTest` e #6 `StreamUrlValidationFlowTest`, com os testes da tabela.
- **Achado novo (#16):** com credenciais e sem URLs, a Home mostra a `EmptyStreamsScreen`, mas o
  `onResumed()` sobe o túnel do mesmo jeito. O `HomeViewModel` põe o `Empty` antes do estado da VPN
  só na UI, e o coletor do ciclo de vida não olha as URLs. O plano esperava `vpn.connectCalls == 0`.
  **Desvio:** o `credentialsWithoutUrlsShowEmptyHome` não afirma a contagem, até decidir se isso
  muda em produção.
  **Decisão: corrigido em produção.** O `HomeViewModel` combina o `_resumed` com "há URL" num
  `StateFlow<Boolean?>` (`tunnelWanted`). O coletor único continua o mesmo, e o `StateFlow` mantém a
  conflação e o descarte de valores repetidos. A Home vazia faz um `disconnect` (o valor inicial é
  "desligado", aplicado como um pause antes de qualquer resume), mas nunca um `connect`. Entraram 3
  testes unitários no `HomeViewModelTest`. Na primeira execução, 2 deles falharam por esperarem
  nenhum `disconnect`; as expectativas foram corrigidas. O
  `credentialsWithoutUrlsShowEmptyHome` passou a afirmar `vpn.connectCalls == 0`.
- No #6, o URL inválido não tem linha para conferir, então "lista igual" vira "mensagem de lista
  vazia ainda visível". No duplicado, a única linha continua e o repositório não muda.
- Robots: o `StreamURLsRobot` ganhou `addUrl`, `assertAddFormOpen`, `assertNoUrls`, `assertRow`,
  `assertDialog` e `confirmDialog` (público). Ele rola a `LazyColumn` até cada nó, inclusive os
  campos do formulário. O `HomeRobot` ganhou `assertEmpty` e `tapEmptyReconfigure`.

**Resultado dos cenários #8, #9 e #10 — feito.** Junto veio a correção do achado #16 em produção.

- #8 `VpnLifecycleFlowTest`: o `FlowTest.moveActivityTo(state)` usa o
  `activityRule.scenario.moveToState`. A Home pausada fica em `LOADING`, e os players saem da
  composição.
- #9 `VpnFailureFlowTest`: o consentimento é concedido no fake, para o "Back to Home" do Config
  estar liberado no segundo teste. O teste também afirma que o `startCalls` fica em 0, porque o
  `ConnectVpnUseCase` só sobe o serviço depois do `connect`.
- #10 `StreamFailureFlowTest`. **Desvio:** o achado #7 foi resolvido pelo `09c146a`, então o card
  com falha tem estado próprio. O teste afirma o overlay de erro no card certo e ganhou um segundo
  teste, `retryConnectsTheStreamOnceItRecovers`, para o "Try again".
- **Desvio comum:** os cenários que precisam de todos os cards conectados semeiam um grid de 2
  colunas, porque o grid dinâmico é preguiçoso.
- `HomeRobot`: `assertPlayback(url, state)`, que lê o `StreamPlaybackStateKey`, e `tapRetry`. A tag
  do botão se repete em todos os cards, então ele é achado pelo card ancestral.
- **Primeira execução completa:** os unitários (324) e a cobertura passaram. Dos 95 instrumentados,
  92 passaram.
  - O `pausingDisconnectsAndResumingReconnects` falhou com "No compose hierarchies found": com a
    activity em `STARTED`, o Compose test rule não acha a hierarquia. A asserção de tela durante a
    pausa saiu, e os contadores e o `streams.connected` cobrem a pausa.
  - Os 2 do #10 falharam no `assertIsDisplayed` do overlay de erro: o teste não considerou o overlay
    exibido. O card tem tamanho, porque usa a proporção
    padrão sem frame, e o nível A afirma o texto e o botão do overlay num player de tela cheia. Os
    testes passaram a ler o estado semântico do player (`FAILED`/`CONNECTING`), o mesmo contrato do
    smoke real. A confirmação visual veio depois (abaixo).
  - Segunda execução, só das duas classes: o #8 passou, e o #10 falhou de novo. O card é
    clicável e funde a semântica dos filhos, então o player só existe na árvore não fundida. O
    `assertPlayback` passou a usar `useUnmergedTree = true`. A fusão também explica o overlay "não
    exibido" (conferência abaixo).
  - Terceira execução, só do #10: os 2 testes passaram. Com a primeira execução completa (o #9 e
    os demais verdes) e a segunda (o #8 verde), os 95 instrumentados estão verdes.
  - **Conferência visual do overlay (feita):** um teste temporário, já apagado, repetiu o cenário
    do #10, tirou um `screencap` e imprimiu a árvore de semântica. O overlay aparece inteiro no card
    de 2 colunas: ícone, "Camera unavailable" e "Try again". A árvore confirmou a fusão: na árvore
    fundida, o nó do overlay não existe, e o texto dele sobe para o card. O seletor achava zero
    nós, e o `assertIsDisplayed` responde "not displayed" em vez de "não encontrado" quando nada
    casa. O botão "Try again" continua na árvore fundida, porque é clicável e tem semântica
    própria; por isso o `tapRetry` funciona sem `useUnmergedTree`. A produção também mostra um
    toast "Failed to connect to camera stream" quando o stream falha.

**Resultado do cenário #7 — feito.** Na primeira execução da classe, os 3 testes passaram.

- `GridOrientationFlowTest`, 3 testes, com os nomes do plano. Ele semeia 3 streams, com grid de 1
  coluna em retrato e de 3 colunas em paisagem, e a ordem de paisagem invertida. Com 3 streams, os
  dois grids cabem numa tela, então todo card é composto e medido.
- `FlowTest.rotateTo(orientation)` recebe o `StreamOrientation` da produção, aplica o
  `requestedOrientation` e espera, com `waitUntil` de 5 s, até a configuração da activity mudar.
  **Desvio:** é o único `waitUntil` dos fluxos, porque a rotação vem do sistema, fora do idle do
  Compose. Um `@After` volta para retrato, para a orientação pedida não vazar se um teste falhar.
- `HomeRobot.assertGrid(columns, order)` lê o layout pelos limites dos cards: a ordem de leitura
  (topo, depois esquerda) e quantos cards dividem a primeira linha. **Desvio:** o plano falava em
  `assertColumns`. Juntar colunas e ordem numa asserção cobre também "a ordem de paisagem".
- `savedGridIsAppliedOnHome`. **Desvio:** a Home chega ao Config pela ação de acessibilidade
  "Reconfigure" do card (`HomeRobot.reconfigureFrom`), porque o FAB e o overscroll são o assunto
  do #11. Depois: passo de URLs, `setGridColumns(PORTRAIT, "2")`, `saveGrid()`, voltar e "Back to
  Home". O teste afirma a pilha `[Home]`, o grid persistido e as 2 colunas na tela. O
  consentimento é concedido no fake, para o "Back to Home" estar liberado.
- `Seed.streams` ganhou `portraitOrder` e `landscapeOrder`, que por padrão repetem as URLs.

**Resultado do cenário #11 — feito.** Na primeira execução da classe, os 4 testes passaram. As 3 execuções que o §9 pede para os gestos foram feitas com o #12 (abaixo).

- `ReconfigureFlowTest`, 4 testes, com os nomes do plano. Todos semeiam credenciais, 2 streams e o
  consentimento concedido no fake, para o "Back to Home" estar liberado.
- O FAB usa um grid fixo 2×1, e o overscroll usa um grid dinâmico de 2 colunas. Com os 2 streams
  numa linha, o grid não rola, e todo swipe conta como overscroll, como no `HomeScreenTest`.
- `returningToHomeDoesNotStackIt` cobre os dois caminhos de volta no mesmo teste: o "Back to
  Home" e o back do sistema. Os dois terminam em `[Home]`.
- `reconfigureCyclesTheTunnel` afirma o achado #12. Abrir o Config faz 1 `disconnect` e 1
  `stop` e solta os streams. Voltar faz o 2º `connect` e o 2º `start` e reconecta os streams.
- `HomeRobot` ganhou `tapReconfigureButton()` e `reconfigureByOverscroll()`, que faz 3 swipes.
  **Desvio:** o `assertShowsStreams` só rola o grid se ele tiver `ScrollToIndex`. No grid fixo, a
  tag `STREAM_GRID` está num container de páginas, que não rola.

**Resultado do cenário #12 — feito.** O #12 e o #11 rodaram juntos 3 vezes seguidas, e os 6 testes passaram nas 3 rodadas. Isso cumpre a exigência do §9 para os gestos, o arraste e o overscroll. Nenhum precisou de `@FlakyTest`.

- `ReorderStreamsFlowTest`, 2 testes, com os nomes do plano. Ele semeia 3 streams, com grid
  dinâmico de 2 colunas em retrato, de 3 em paisagem, e a ordem de paisagem invertida.
- `draggingAStreamPersistsTheNewOrder` arrasta a alça do primeiro card até o segundo. Na tela, a
  ordem vira `[1, 0, 2]`, e o `portraitOrder` persistido também. O conjunto de URLs não muda.
- `reorderingKeepsTheOtherOrientation`: o `landscapeOrder` persistido segue o semeado. **Desvio:** o
  teste também gira para paisagem e confere essa ordem na tela, com o `rotateTo` do #7.
- `HomeRobot.dragOnto(from, onto)` usa o mesmo gesto do `HomeScreenTest`: `down`, 10 `moveBy` e
  `up`, na árvore não fundida, porque o card funde a alça.
- O arraste do modo fixo segue fora, como o plano previa.

**Critérios de pronto (§8) — primeira passada.**

- A cobertura passou. A suíte inteira em `en` passou 3 vezes seguidas, em 382 s, 381 s e 375 s.
  O `assembleRelease` passou.
- Em `pt-BR`, falharam 3 testes:
  - `GridOrientationFlowTest.rotatingSwitchesToLandscapeGrid` e
    `ReorderStreamsFlowTest.reorderingKeepsTheOtherOrientation`: depois de girar, a Home seguia com
    o grid de retrato. **Causa:** o `InTestLocale` montava o contexto localizado com
    `remember(context, locale)`, e o `LocalConfiguration` que ele fornece ficava congelado na
    configuração da primeira composição. Em `en` o wrapper não entra, por isso o `en` passou.
    **Correção:** o wrapper é refeito a partir do `LocalConfiguration` atual. O `rotateTo` agora
    espera a orientação que a composição aplicou (`SideEffect` no `launchApp`), e não a dos
    recursos da activity. Assim, um conteúdo que não acompanha a rotação estoura o tempo no próprio
    `rotateTo`.
  - `FakeVpnPermissionRepositoryTest.refusingTheDialogKeepsTheConsentRequired`: o corpo passou, mas
    no encerramento a activity ficou presa em `PAUSED` ("never becomes DESTROYED"). Não tem relação
    com o idioma, e parece coisa do emulador. A execução parou ali, e os 10 testes seguintes do
    pacote `testing` não rodaram: o relatório tem 94 testes, não 104. Fica em observação na nova
    rodada.

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

- [x] `./gradlew :app:connectedDebugAndroidTest` verde no `Pixel_8a`, em `en` e em `pt-BR`.
- [x] A suíte inteira 3x seguidas sem falha intermitente.
- [x] `./gradlew :app:jacocoTestCoverageVerification` continua verde.
- [x] `assembleRelease` sem a `HiltTestActivity`, e o app real sem mudança visível.
- [x] Tempo da suíte anotado (antes e depois do Orchestrator).
- [x] Documentação do §6 atualizada.

**Resultado do §8 — feito.** Detalhes na seção "Critérios de pronto (§8) — primeira passada" e
abaixo:

- Segunda execução em `pt-BR`, depois da correção do `InTestLocale`: os 104 testes passaram, em
  6 min 37 s. As duas classes de rotação também passaram em `en`, depois da mudança do `rotateTo`.
- O `FakeVpnPermissionRepositoryTest` passou. A activity presa em `PAUSED` não se repetiu, então
  fica registrada como falha isolada do emulador.
- Release: o manifesto mesclado só cita a `HiltTestActivity` num comentário, e o `classes.dex` do
  APK não tem o nome dela. A única mudança de comportamento no app real é a correção do achado #16
  (a Home vazia não sobe o túnel), que foi decisão de produto.
- Tempo: o "antes e depois" foi medido no §3.1, com os 36 testes de então: 49 s sem o
  Orchestrator e 128 s com ele (já no `architecture.md`). Hoje, com o Orchestrator, a suíte de 104
  testes leva cerca de 6 min 20 s em `en` (382 s, 381 s e 375 s) e 6 min 37 s em `pt-BR`. O tempo
  não atrapalhou, então o caminho alternativo do §9 (`StorageModule` com arquivo temporário, sem
  `clearPackageData`) não foi necessário.

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
