# Testes instrumentados no Cameras App: por onde começar, abordagem e tecnologias

Relatório baseado na leitura do código atual (`main` @ `0424d58`). Cada recomendação aponta o
trecho do código que a justifica.

---

## 1. Resumo

- **Comece pelas telas puras (`*Screen`) com Compose UI Test**, sem Hilt e sem navegação. É o
  que tem o menor custo de infraestrutura e o maior retorno imediato, e o próprio
  `docs/architecture.md` §9.2 já aponta `ConfigScreen`, `StreamURLsScreen` e `EmptyStreamsScreen`
  como os testes de maior valor.
- **Depois, rode o app inteiro com Hilt Testing e troque só o que sai do processo** (túnel
  WireGuard, WebRTC/WHEP, permissões e a câmera). Todo o resto continua real: navegação,
  ViewModels, use cases e o DataStore criptografado.
- **Os diálogos do sistema (consentimento de VPN e permissão de câmera) ficam numa suíte
  separada com UI Automator**, e o teste com túnel e Pi de verdade fica num smoke manual ou
  noturno em dispositivo físico.
- **No CI, use Gradle Managed Devices** com uma imagem ATD, num job separado e, no começo, não
  obrigatório.

- **Dados de QR:** as fixtures fictícias de `qr-code-gen/examples/` cobrem a leitura, e um QR
  real, de um peer exclusivo e fornecido por quem roda o teste, cobre só a VPN (§5). O teste com
  VPN real é disparado **somente à mão, na máquina de quem tem o QR**. O runner self-hosted fica
  para depois (§8.2), e o runner hospedado nunca roda esse teste.

Antes do primeiro teste de fluxo, há **ajustes pequenos no código de produção** (§4). Sem eles, os
testes ficam frágeis ou simplesmente não conseguem cobrir alguns pontos. A remoção do payload de QR
dos logs (§4.4) já foi feita.

---

## 2. Ponto de partida

| Item | Situação hoje | Onde |
|---|---|---|
| `androidTest/` | Só o `ExampleInstrumentedTest` gerado | `app/src/androidTest/java/com/gdisys/cameras/` |
| Dependências instrumentadas | `androidx-junit`, `espresso-core`, `ui-test-junit4` (Compose BOM), `ui-test-manifest` (debug) | `app/build.gradle.kts` |
| Runner | `androidx.test.runner.AndroidJUnitRunner` (sem suporte a Hilt) | `defaultConfig.testInstrumentationRunner` |
| `testTag` / semântica | **Nenhum** no código | `grep testTag` vazio |
| Idiomas | `values`, `values-en`, `values-pt-rBR` | `app/src/main/res/` |
| DI | Hilt 2.59.2, com interfaces em todas as fronteiras externas | `core/*/di/` |
| CI | `[DEV] BUILD` com `unit-tests` e `build`; sem job instrumentado | `.github/workflows/dev-build.yml` |
| Application id do debug | `com.gdisys.cameras.debug` | `buildTypes.debug.applicationIdSuffix` |

O projeto já está bem preparado para isso. O padrão Route/Screen deixa as telas puras
(recebem `uiState` e callbacks), e todas as integrações externas passam por uma interface
injetada:

| Fronteira externa | Interface | Módulo Hilt | Componente |
|---|---|---|---|
| Túnel WireGuard | `VpnRepository` | `VpnModule` | `SingletonComponent` |
| Serviço de ciclo de vida da VPN | `VpnLifecycleController` | `VpnModule` | `SingletonComponent` |
| Permissões | `VpnPermissionRepository`, `CameraPermissionRepository` | `PermissionModule` | `SingletonComponent` |
| Sinalização WHEP (HTTP) | `WhepRemoteDataSource` | `WhepNetworkModule` | `SingletonComponent` |
| Sessão WebRTC | `WhepClient` | `WhepClientModule` | `ViewModelComponent` |
| Conexões de vídeo da Home | `StreamConnectionRepository` | `WhepConnectionModule` | `ViewModelComponent` |
| DataStore (credenciais / streams) | `DataStore<UserPreferences>`, `DataStore<StreamPreferences>` | `StorageModule` | `SingletonComponent` |

---

## 3. O que torna este app difícil de testar (e como contornar)

| # | Obstáculo | Onde no código | Saída recomendada |
|---|---|---|---|
| 1 | **Scanner de QR preso ao CameraX + ML Kit**. `QrCodeScreen` cria `ProcessCameraProvider` e `QrCodeAnalyzer` direto no composable, sem nenhuma interface no meio | `feature/qrcode/components/QrCodeScreen.kt` | Nos fluxos, injetar o JSON das fixtures (§5) no `savedStateHandle[QR_CODE_RESULT_KEY]` da entrada `Config` (o contrato que `NavigationRoot` já usa). A decodificação da imagem é testada à parte, passando os PNGs pelo ML Kit |
| 2 | **O consentimento de VPN não passa pelo fake.** O ViewModel decide pelo `RequestVpnPermissionUseCase` (fakeável), mas o `ConfigRoute` chama `VpnService.prepare(context)` **direto** para montar o `Intent` | `feature/config/ConfigRoute.kt` (`mapNotNull { VpnService.prepare(context) }`) | Nos testes herméticos, deixar a permissão de VPN como já concedida. Para cobrir o diálogo, usar UI Automator ou o `appops`. Opcionalmente, mover a criação do `Intent` para trás de uma interface (§4.2) |
| 3 | **Os toasts são `android.widget.Toast`**, uma janela do sistema que o Compose UI Test não enxerga, e no API 30+ nem o UI Automator lê de forma confiável | `core/components/ToastDisplayer.kt` | Validar o *efeito* (botão em `Error`/`Done`, tela que não mudou). Se a mensagem precisar ser verificada, injetar um `ToastPresenter` (§4.2) |
| 4 | **Túnel WireGuard real** via `GoBackend` | `core/vpn/data/VpnRepositoryImpl.kt` | `FakeVpnRepository` com `MutableStateFlow<VpnTunnelState>` |
| 5 | **WebRTC precisa do Pi e de mídia real**. `WebRtcVideoPlayer` ainda cria um `SurfaceViewRenderer` com o `EglBase` | `feature/cameras/components/WebRtcVideoPlayer.kt`, `core/webrtc/di/WhepRtcModule.kt` | Trocar o `StreamConnectionRepository` (no `ViewModelComponent`) por um fake. O `EglBase` real pode continuar, porque ele sobe no emulador sem precisar de rede |
| 6 | **Estado persistente entre testes**. Os DataStores são delegates `by dataStore(...)` com nome de arquivo fixo e chave no AndroidKeyStore | `core/storage/data/DataStore.kt`, `StreamPreferencesDataStore.kt` | Test Orchestrator com `clearPackageData`, ou um `StorageModule` de teste com arquivo temporário por teste (§6.3) |
| 7 | **Texto muda com o locale** (pt-BR/en) | `res/values-*` | `testTag` para encontrar nós, ou `activity.getString(R.string.x)` quando o teste for sobre o texto |
| 8 | **O ciclo de vida liga e desliga a VPN**: a Home conecta no `ON_RESUME` e desconecta no `ON_PAUSE`, e o `VpnLifecycleService` desconecta no `onDestroy` | `feature/cameras/HomeRoute.kt`, `core/vpn/data/VpnLifecycleService.kt` | O fake registra as chamadas. O `ActivityScenario.moveToState(...)` provoca os eventos |
| 9 | **Câmera é `required="true"`** no manifest | `AndroidManifest.xml` | Usar um AVD com câmera emulada (o padrão). Isso só importa nos testes do scanner |

---

## 4. Ajustes recomendados no código de produção (antes dos fluxos)

Todos são pequenos, seguem MVVM e não mudam comportamento.

### 4.1 `testTag` centralizados por feature

Criar um objeto de tags em `main` por feature (ex.: `ConfigTestTags`, `StreamURLsTestTags`,
`HomeTestTags`), para que tela e teste usem as mesmas constantes. Pontos mínimos:

- `ConfigScreen` / `ConfigurationStepButton`: os quatro passos (câmera, QR, VPN, URLs) e o
  botão "ir para Home". Também convém expor o `ConfigButtonState` como semântica
  (`stateDescription`), para o teste afirmar `Done`/`Error` sem depender de ícone ou cor.
- `StreamURLsScreen`, `AddStreamUrlForm`, `StreamUrlRow`, `GridPreferencesSection`.
- `HomeScreen`, `CameraGridItem`, `EmptyStreamsScreen`, `DraggableReconfigureButton`.

`testTag` não tem custo em runtime que valha a pena discutir. Registrar a convenção no
`architecture.md`.

### 4.2 Duas costuras opcionais

- **Intent de consentimento de VPN**: hoje há duas chamadas a `VpnService.prepare`, uma no
  repositório e outra no route. Se o route pedir o `Intent` a uma interface injetável (o mesmo
  contrato do `VpnRepository.getVpnPermissionIntent()`), os testes herméticos passam a cobrir o
  caminho "pedir consentimento → aceito/negado" com um `Intent` falso para uma Activity de teste.
  Sem isso, esse caminho só se testa com UI Automator.
- **Toast**: um `ToastPresenter` injetado no lugar de `Toast.makeText` permite verificar
  *qual* mensagem saiu. É opcional: dá para começar validando só o estado.

### 4.3 Fonte do QR

**Decidido:** injeção no `savedStateHandle`. O teste pega o `NavController`, ou navega até
`Config` e usa o `TestNavHostController`, e define
`currentBackStackEntry.savedStateHandle[QR_CODE_RESULT_KEY] = json`. Isso exercita exatamente o
contrato que o `QrCodeRoute` usa ao voltar, sem mudar a produção. Ler a imagem pela câmera fica
fora dos fluxos e é coberto pelo teste de decodificação (§5.1).

Uma interface `QrCodeSource` injetável (produção = CameraX + ML Kit; teste = JSON fixo) só
volta à mesa se a tela do scanner virar alvo de teste.

### 4.4 Nada de payload de QR no logcat (feito)

Um QR real carrega a chave privada e a PSK do WireGuard, e o logcat é legível via adb e vai parar
nos artefatos de falha do CI. Três pontos vazavam o conteúdo e foram corrigidos:

| Onde | Antes | Agora |
|---|---|---|
| `feature/qrcode/QrCodeRoute.kt` | `Log.d(DEBUG_TAG, "QrCodeRoute: $rawValue")` | Removido |
| `feature/qrcode/components/QrCodeAnalyzer.kt` | `Log.d("QrCodeAnalyzer", "Scanned content: $rawValue")` | Removido |
| `feature/config/ConfigViewModel.kt` (`onQrCodeScanned`, falha) | `Log.e(DEBUG_TAG, "QR Code scan error", e)` | Só o tipo da exceção: a `SerializationException` do kotlinx cita o trecho do JSON de entrada na mensagem |

A regra está registrada no `docs/architecture.md` §5.3. Nos testes, vale a mesma regra: nunca
imprimir o JSON real em asserções (`assertEquals(json, ...)` imprime os dois lados quando falha).

---

## 5. Dados de teste: QR fictícios e QR real

**Decisão:** os QR fictícios já versionados cobrem o comportamento de leitura, e um QR real,
fornecido por quem roda o teste, fica restrito aos testes que sobem a VPN.

### 5.1 Fixtures fictícias (`qr-code-gen/examples/`)

| Fixture | Conteúdo | Resultado esperado no app (o teste confirma) |
|---|---|---|
| `1_validqr_bogus_info` | Todos os campos, valores inventados | Parse aceito → passo do QR `Done`. Útil também para o caminho "credenciais válidas no formato, mas o túnel não sobe" |
| `2_validqr_incomplete_info` | Sem `pPuk` | `isValid()` falha → `QR_CODE_INVALID_DATA_ERROR`, passo em `Error` |
| `3_validqr_info_dif_type` | `pPersistentKeepAlive` numérico | Decodificação falha → `QR_CODE_FORMAT_ERROR`, passo em `Error` |
| `4_validqr_dif_type_2` | `{"example": "value"}` | Decodificação falha → `QR_CODE_FORMAT_ERROR` |

Como usar:

- **Os fluxos (nível B) leem o `.json`**, que vira a única fonte de verdade. Para não duplicar os
  arquivos, inclua o diretório como assets do `androidTest`:
  `sourceSets["androidTest"].assets.srcDir("$rootDir/qr-code-gen/examples")`, e leia com
  `InstrumentationRegistry.getInstrumentation().context.assets`.
- **Teste de decodificação da imagem:** um teste instrumentado passa cada `.png` pelo ML Kit
  (`InputImage.fromBitmap` + `BarcodeScanning.getClient(...)`) e compara o texto lido com o
  `.json` ao lado. Cobre o decodificador no dispositivo sem depender da câmera do emulador.
- **Os PNGs provavelmente estão desatualizados:** os `.json` 1–3 mudaram em 20/10 (commit
  `8495c35`, `pAllowedips` do split tunnel) e os PNGs são de 01/09. O teste acima pega isso. Por
  enquanto, basta regerar os PNGs a partir dos `.json`. O `main.py` hoje só lê o `vpn.json` e
  nomeia pelo `iAddr`, então vale um modo que gere um PNG para cada `.json` de `examples/`.

### 5.2 QR real (só para os testes com VPN)

- **Formato:** o JSON, não o PNG. A imagem não acrescenta nada ao teste e é mais difícil de
  tratar como segredo. O `vpn.json` local é uma *lista* de peers, e o teste recebe *um* objeto.
- **Origem local:** um arquivo fora do git (o `qr-code-gen/.gitignore` já ignora `vpn*.json`),
  apontado por variável de ambiente, por exemplo `CAMERAS_E2E_QR_FILE`.
- **Na esteira: adiado.** Por ora, o QR real não vai para o GitHub. Quando o runner self-hosted
  vier (§8.2), ele entra num secret de *environment* (ex.: `real-vpn` / `CAMERAS_E2E_QR_JSON`) com
  aprovação obrigatória.
- **Um peer exclusivo para testes**, não a chave de um celular de verdade. Dois clientes com a
  mesma chave disputam o endpoint no WireGuard (o roaming troca de lado a cada handshake), então o
  teste derrubaria o celular de alguém. Com um peer próprio, a chave é revogável sozinha e o
  MikroTik pode liberar para ela só o Pi (WHEP).
- **Como chega ao teste:** é preferível empurrar um arquivo do que passar um argumento de
  instrumentação. O argumento aparece na linha de comando do Gradle e do `am instrument`. Uma
  task Gradle (ex.: `pushRealQrPayload`) lê `CAMERAS_E2E_QR_FILE`, faz `adb push` para
  `/data/local/tmp/cameras-e2e-qr.json` antes dos testes e apaga o arquivo do dispositivo no fim,
  mesmo se algum teste falhar. Sem a variável, os testes `@RealEnvironment` são pulados
  (`assumeTrue`), em vez de falharem.
- **Como rodar:** com o celular plugado e o QR num arquivo local,
  `CAMERAS_E2E_QR_FILE=~/caminho/peer-ci.json ./gradlew :app:connectedE2eReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.gdisys.cameras.e2e.RealEnvironment`.
  Documentar isso num README curto ao lado dos testes.
- **"Conectado" não basta:** `VpnTunnelState.CONNECTED` vem do `Tunnel.State.UP` do `GoBackend`
  e só indica que a interface subiu. O WireGuard não tem "conexão", então o teste precisa provar
  alcance pelo túnel: o WHEP do Pi respondendo ou um frame renderizado (`onFirstFrameRendered`).

---

## 6. Abordagem: pirâmide em três níveis

| Nível | Real | Falso | Ferramenta | Onde roda | Quando |
|---|---|---|---|---|---|
| **A. Tela isolada** | Composable, recomposição, semântica | Tudo fora da tela (estado e callbacks passados à mão) | Compose UI Test (`createComposeRule`) | Emulador (CI) | Todo PR |
| **B. Fluxo hermético** | Activity, Hilt, navegação, ViewModels, use cases, DataStore criptografado | VPN, WHEP/WebRTC, permissões, QR | Compose UI Test + Hilt Testing + `ActivityScenario` | Emulador (CI) | Todo PR |
| **C. Sistema / real** | + diálogos do sistema; no smoke, também o túnel, o Pi e o build release minificado | Nada (smoke) ou só o backend (sistema) | UI Automator; argumentos de instrumentação | Dispositivo físico / emulador local | Manual, noturno ou antes de release |

O nível C automatiza o smoke manual que o `architecture.md` §2 e §10.1 exigem a cada mudança de
keep rules, de modelo `@Serializable` ou de dependências de VPN/WebRTC (QR → túnel → stream
tocando). É o único que pega erros do R8 em runtime.

### 6.1 Nível A: telas isoladas (comece aqui)

> **Feito**, conforme o plano `e2e_level_a_plan.md`: `ConfigScreenTest`, `StreamURLsScreenTest` e
> `EmptyStreamsScreenTest`, 30 testes, documentados no `docs/architecture.md` §10.

```kotlin
class ConfigScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun homeButtonRequestsNavigationWhenClicked() {
    var requested = false
    composeRule.setContent {
      CamerasTheme {
        ConfigScreen(uiState = ConfigUiState(/* ... */), onNavigateToHome = { requested = true }, /* ... */)
      }
    }
    composeRule.onNodeWithTag(ConfigTestTags.NAVIGATE_TO_HOME).performClick()
    assertTrue(requested)
  }
}
```

Alvos, na ordem:

1. `ConfigScreen`: as renderizações de cada `ConfigButtonState` (`Ready`, `Loading`, `Done`,
   `Error`) e o callback de cada passo.
2. `StreamURLsScreen`: formulário de adicionar, remover linha, diálogos de "restaurar padrões",
   "salvar" e "descartar alterações", e as flags de alteração por seção.
3. `EmptyStreamsScreen`: o atalho para `Config`.
4. `GridPreferencesSection`: entrada de colunas/linhas por orientação.

Não precisa de Hilt, de runner customizado nem de fakes, então dá para começar ainda hoje.

### 6.2 Nível B: fluxos herméticos com Hilt

Infraestrutura:

- `HiltTestRunner` (subclasse de `AndroidJUnitRunner` que instancia `HiltTestApplication`),
  configurado em `testInstrumentationRunner`.
- `@HiltAndroidTest` + `HiltAndroidRule(order = 0)` + `createAndroidComposeRule<MainActivity>()`
  (`order = 1`).
- Fakes em `androidTest/.../fake/`, instalados com `@TestInstallIn(replaces = [...])`. É uma
  troca global, que compila um único grafo para toda a suíte e é bem mais rápida de buildar que
  `@UninstallModules` por classe. Deixe `@UninstallModules` + `@BindValue` só para o teste que
  realmente precisa de um comportamento diferente.

| Fake | Substitui | Controle exposto ao teste |
|---|---|---|
| `FakeVpnRepository` | `VpnModule` (binding de `VpnRepository`) | `MutableStateFlow<VpnTunnelState>`; contadores de `connect`/`disconnect`; falha programável; `getVpnPermissionIntent()` retorna `null` |
| `FakeVpnLifecycleController` | `VpnModule` | Contadores de `start`/`stop` (evita subir o `VpnLifecycleService`) |
| `FakeVpnPermissionRepository` / `FakeCameraPermissionRepository` | `PermissionModule` | Booleano configurável |
| `FakeStreamConnectionRepository` | `WhepConnectionModule` (`ViewModelComponent`) | Registra URLs conectadas; `onError` programável por URL |
| `FakeWhepClient` | `WhepClientModule` (`ViewModelComponent`) | Só para o grafo fechar, já que o `WhepConnectionManager` real deixa de ser usado |
| DataStores de teste | `StorageModule` | Ver §6.3 |

> Como `VpnModule` e `PermissionModule` registram dois bindings cada um, o módulo de teste que
> os substitui precisa fornecer **os dois**.

Cenários, cada um em uma classe:

| # | Cenário | O que verifica |
|---|---|---|
| 1 | Primeira execução | `Loading → Config` quando não há credenciais (`InitViewModel`) |
| 2 | Onboarding completo | Câmera concedida → QR injetado → VPN concedida → adicionar URL → `Home` com o grid |
| 3 | QR inválido | JSON malformado / sem credenciais → passo do QR em `Error`, sem sair de `Config` |
| 4 | Gate do Config | Botão Home e Back bloqueados enquanto faltar um requisito (`missingRequirement`) |
| 5 | Retorno com credenciais | DataStore pré-preenchido → app abre direto na `Home` |
| 6 | Validação de URL | URL inválida e duplicada rejeitadas (`StreamUrlValidation`) |
| 7 | Grid por orientação | Mudar linhas/colunas, girar a tela (`scenario.onActivity { requestedOrientation = ... }`); o layout segue a orientação |
| 8 | Ciclo de vida da VPN | `moveToState(STARTED)` → `disconnect`; `moveToState(RESUMED)` → `connect` |
| 9 | Falha da VPN na Home | `connect` falha → volta para `Config` sem duplicar destinos |
| 10 | Falha de stream | `onError` do fake → card em estado de erro, app de pé |
| 11 | Reconfigurar a partir da Home | `DraggableReconfigureButton` / overscroll → `Config`; voltar à Home não empilha uma segunda `Home` (`navigateToHome`) |
| 12 | Reordenar streams | Drag no `ReorderableGrid` persiste a ordem (`UpdateStreamOrderUseCase`) |

O #12 depende de gesto e tende a ser o mais instável. Deixe-o por último.

Padrão de escrita: **Robot / Page Object** (`ConfigRobot`, `StreamURLsRobot`, `HomeRobot`), para
que o teste leia como o fluxo do usuário e as tags fiquem num só lugar.

### 6.3 Isolamento de estado (DataStore + Keystore)

Os delegates `by dataStore(fileName = ...)` criam uma instância por processo para um arquivo
fixo, e dois `DataStore` no mesmo arquivo lançam exceção. Há dois caminhos:

1. **Test Orchestrator com `clearPackageData = "true"`**: cada teste roda num processo novo e com
   os dados do app apagados. É o mais simples e o mais fiel (o DataStore criptografado real é
   exercitado), mas cada teste fica alguns segundos mais lento. A chave do AndroidKeyStore *não*
   é apagada pelo `clearPackageData`, o que não é problema porque o arquivo cifrado some e o
   serializer recria com o valor padrão.
2. **`StorageModule` de teste** com `DataStoreFactory.create(...)` num arquivo temporário único
   por teste, usando o mesmo `EncryptedPreferencesSerializer`. É mais rápido e permite pré-popular
   o estado (cenário #5), mas exige expor o serializer e o alias de chave hoje `private` em
   `DataStore.kt`.

**Recomendação:** o Orchestrator desde o início, e o caminho 2 se o tempo de suíte incomodar.
Para pré-popular o estado no cenário #5, injete o `DataStore<UserPreferences>` no teste
(`@Inject`, depois de `hiltRule.inject()`) e grave antes de lançar a Activity. Para isso, use
`ActivityScenario.launch` manual em vez da compose rule que lança na criação.

### 6.4 Nível C: sistema e smoke real

- **UI Automator** para o que fica fora do processo:
  - Diálogo de consentimento de VPN (pacote `com.android.vpndialogs`). **Não** é permissão
    runtime, então `GrantPermissionRule` não funciona. Clique no diálogo com `UiDevice`/`By`, ou
    pré-conceda com `adb shell appops set com.gdisys.cameras.debug ACTIVATE_VPN allow`.
  - Permissão de câmera: aceitar, negar e "negar sempre". O último cobre o toast
    `CAMERA_PERMISSION_PERMANENTLY_DENIED` e o caminho de `shouldShowRequestPermissionRationale`.
  - Nesses testes, use o `VpnPermissionRepositoryImpl` e o `CameraPermissionRepositoryImpl`
    **reais**.
- **Smoke real** (`@RealEnvironment`, fora do CI hospedado): o QR real do §5.2 (arquivo empurrado
  para o dispositivo) e as URLs → `VpnTunnelState.CONNECTED` → ao menos um stream renderiza frame.
  Para detectar o frame, exponha um sinal semântico a partir do `onFirstFrameRendered` que hoje é
  vazio em `WebRtcVideoPlayer`. Rode contra o **release minificado** (um build type derivado do
  release, assinado com a debug key), porque é ali que as keep rules quebram. Roda na máquina de
  quem fornece o QR (§5.2). O runner self-hosted (§8.2) fica para depois.

---

## 7. Tecnologias

| Tecnologia | Papel aqui | Status |
|---|---|---|
| **AndroidX Test** (`runner`, `rules`, `ext:junit`, `core` / `ActivityScenario`) | Base de tudo: runner, ciclo de vida da Activity, `InstrumentationRegistry` | `ext:junit` já está; adicionar `runner` e `rules` |
| **Compose UI Test** (`ui-test-junit4`) | Encontrar e interagir com os composables; sincronização com a recomposição | Já está |
| **Espresso** | Por baixo do Compose Test (idling). Quase não será usado direto, porque o app é 100% Compose | Já está |
| **Hilt Testing** (`com.google.dagger:hilt-android-testing` + `kspAndroidTest(hilt-compiler)`) | Trocar VPN, WebRTC, permissões e storage | Adicionar, **na mesma versão do Hilt (2.59.2)** |
| **Navigation Testing** (`androidx.navigation:navigation-testing`) | `TestNavHostController`, se o QR for injetado pelo `savedStateHandle` fora da Activity | Opcional |
| **Test Orchestrator** (`androidx.test:orchestrator`) | Um processo por teste + `clearPackageData` | Adicionar |
| **UI Automator** (`androidx.test.uiautomator:uiautomator`) | Diálogos do sistema (VPN, câmera) | Adicionar no nível C |
| **Turbine / coroutines-test** | Já usados nos unit tests; úteis nos fakes, se for preciso observar `Flow` | Já existem em `testImplementation`; repetir em `androidTestImplementation` só se usar |
| **Gradle Managed Devices** | Emulador declarado no `build.gradle.kts`, igual no local e no CI | Adicionar |

Dispensáveis neste momento:

- **MockK em `androidTest`**: o `mockk-android` funciona, mas fakes escritos à mão leem melhor e
  combinam com os `StateFlow` do projeto.
- **Robolectric** para rodar Compose na JVM: daria velocidade, mas dividiria a suíte em dois
  mundos, e a pergunta aqui é sobre testes instrumentados.
- **Maestro / Appium** (caixa-preta): úteis para comparar abordagens ou para um smoke sem código,
  mas não conseguem trocar dependências, que é o que torna os fluxos B estáveis.
- **Screenshot testing** (Roborazzi / Compose Preview Screenshot Testing): é um eixo diferente
  (regressão visual). Fica para depois.

Esboço da configuração (`app/build.gradle.kts`):

```kotlin
android {
  defaultConfig {
    testInstrumentationRunner = "com.gdisys.cameras.HiltTestRunner"
    testInstrumentationRunnerArguments["clearPackageData"] = "true"
  }
  testOptions {
    execution = "ANDROIDX_TEST_ORCHESTRATOR"
    animationsDisabled = true
    managedDevices {
      localDevices {
        create("pixel6Api34") {
          device = "Pixel 6"
          apiLevel = 34
          systemImageSource = "aosp-atd"
        }
      }
    }
  }
}

dependencies {
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.hilt.android.testing)
  kspAndroidTest(libs.hilt.compiler)
  androidTestUtil(libs.androidx.test.orchestrator)
}
```

> Antes de fixar as versões no `libs.versions.toml`, confirmar a compatibilidade com AGP 9.1 e o
> nome atual da DSL do GMD (`managedDevices.localDevices` vs. `allDevices`), que mudou entre
> versões do AGP.

---

## 8. CI

Dois lugares diferentes, porque os testes têm necessidades diferentes:

| Suíte | Onde roda | Gatilho | Por quê |
|---|---|---|---|
| Níveis A e B (herméticos) | Runner **hospedado** do GitHub (`ubuntu-latest` + GMD) | Todo PR / push em `main` | Não precisam de rede nem de segredo |
| Nível C com VPN real | **Máquina de quem tem o QR** + celular físico (no futuro, runner self-hosted) | Manual, pelo Gradle | Precisa alcançar o endpoint WireGuard por IPv6 e o Pi, e precisa do QR real |
| Nível C só com diálogos do sistema | Qualquer um dos dois (emulador) | Manual ou agendado | Não precisa de rede, mas é mais instável |

### 8.1 Runner hospedado (herméticos)

- Novo job `instrumented-tests` no `[DEV] BUILD`, rodando
  `./gradlew :app:pixel6Api34DebugAndroidTest`, filtrando fora `@RealEnvironment` e os testes de
  UI Automator (com `notAnnotation`), e com upload de
  `app/build/outputs/androidTest-results/` e `app/build/reports/androidTests/` quando falhar.
- O `ubuntu-latest` do GitHub tem KVM, que o GMD usa. A alternativa é a action
  `reactivecircus/android-emulator-runner`, mas o GMD mantém o emulador declarado no build, o que
  bate com a filosofia do projeto de deixar as regras no Gradle e não no workflow (como já é feito
  com o `minimumLineCoverage`).
- Custo esperado: 8 a 15 minutos por execução, com cache do Gradle. Para reduzir, dá para rodar
  só em PRs que tocam `app/src/**`.
- **Começar como não obrigatório** por ~2 semanas e torná-lo obrigatório quando estiver estável.
- O JaCoCo continua só com unit tests. A cobertura de `androidTest`
  (`enableAndroidTestCoverage`) pode ser estudada depois, mas não deve entrar no gate de 90%, que
  é deliberadamente sobre o escopo testável na JVM.
- O runner hospedado **não serve para o teste com VPN real**: ele não tem conectividade IPv6, e o
  endpoint WireGuard de casa é IPv6 (o IPv4 está atrás de CGNAT). Além disso, a rede do emulador e
  o consentimento de VPN aumentariam a instabilidade.

### 8.2 Runner self-hosted (VPN real): **adiado**

> Decisão atual: o teste com VPN real roda só à mão, na máquina de quem tem o QR (§5.2). Esta
> seção fica como referência para quando a automação valer a pena.

**O que é.** Uma máquina sua com o agente
[`actions/runner`](https://docs.github.com/actions/hosting-your-own-runners) instalado. O agente
abre uma conexão **de saída** (HTTPS long-poll) para o GitHub e busca os jobs cujo `runs-on` bate
com os labels dele. Não precisa abrir nenhuma porta de entrada. O workflow pede
`runs-on: [self-hosted, linux, cameras-real-vpn]`, e o job roda nessa máquina, com acesso ao que
ela alcança: a internet IPv6 e um celular plugado por USB.

**Montagem sugerida:**

| Peça | Recomendação | Motivo |
|---|---|---|
| Máquina | Um mini-PC / notebook velho com Linux (x86_64), dedicado | O Gradle e o build do APK precisam de 8 GB+ de RAM. Uma máquina dedicada isola o risco (ver segurança). O Mac pessoal funciona para começar, mas mistura o runner com seus dados |
| Dispositivo | **Celular Android físico** plugado por USB, sempre na tomada | `VpnService`, câmera e decodificação de vídeo reais. O emulador tem IPv6 e VPN pouco confiáveis |
| Rede | **Fora da LAN de casa** (outra rede ou 4G/5G do próprio celular) ou, se ficar em casa, numa **VLAN isolada** no MikroTik | De fora, o teste cobre o caminho real (ISP, pinhole IPv6, endpoint). De dentro da LAN, o tráfego faz hairpin e não exercita o pinhole |
| Peer WireGuard | Exclusivo do CI (§5.2), com `AllowedIPs` no MikroTik limitado ao Pi | Revogável sozinho, não derruba o celular de ninguém e limita o que um vazamento alcança |
| Pi | Precisa estar com câmeras reais ou um stream de teste no MediaMTX | O teste afirma "frame renderizado", então precisa haver vídeo do outro lado |

**Preparação do celular (uma vez):** opções de desenvolvedor + depuração USB autorizada para o
usuário do runner, "permanecer ativo" ligado, sem bloqueio de tela, animações desligadas
(`settings put global window_animation_scale 0`, idem `transition_` e `animator_duration_scale`).
O consentimento de VPN vem do `appops set <pacote> ACTIVATE_VPN allow`, ou fica para o próprio
teste de UI Automator aceitar.

**Esboço do workflow** (`.github/workflows/real-vpn-smoke.yml`):

```yaml
name: "[E2E] REAL VPN SMOKE"
on:
  workflow_dispatch:
  schedule:
    - cron: "0 6 * * *"        # 03:00 BRT
# Never pull_request: this repository is public, and a PR would run its own code on this machine.

permissions:
  contents: read

concurrency:
  group: real-vpn-smoke        # one device, one run at a time
  cancel-in-progress: false

jobs:
  smoke:
    runs-on: [self-hosted, linux, cameras-real-vpn]
    environment: real-vpn      # holds CAMERAS_E2E_QR_JSON; requires approval
    timeout-minutes: 40
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4

      - name: Push the real QR payload to the device
        env:
          QR_JSON: ${{ secrets.CAMERAS_E2E_QR_JSON }}
        run: |
          umask 077
          printf '%s' "$QR_JSON" > "$RUNNER_TEMP/qr.json"
          adb push "$RUNNER_TEMP/qr.json" /data/local/tmp/cameras-e2e-qr.json
          rm -f "$RUNNER_TEMP/qr.json"

      - name: Run the real-environment tests against the minified build
        run: >
          ./gradlew :app:connectedE2eReleaseAndroidTest
          -Pandroid.testInstrumentationRunnerArguments.annotation=com.gdisys.cameras.e2e.RealEnvironment

      - name: Remove the payload from the device
        if: always()
        run: adb shell rm -f /data/local/tmp/cameras-e2e-qr.json

      - name: Upload the test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: real-vpn-smoke-reports
          path: app/build/reports/androidTests/
```

(`e2eRelease` é o build type derivado do release e assinado com a debug key, citado no §6.4. O
nome é uma sugestão.)

**Segurança (o repositório `gdinn/cameras.casa` é público):**

- **Nunca** dispare esse job em `pull_request` ou `pull_request_target`. O GitHub desaconselha
  runner self-hosted em repositório público justamente porque um PR de fork rodaria código
  arbitrário na sua máquina e na sua rede. Os gatilhos ficam só `workflow_dispatch` e `schedule`,
  que rodam código já mesclado em `main`.
- Em *Settings → Actions → Runner groups*, restrinja o grupo do runner a este workflow. Em
  *Settings → Actions → General*, exija aprovação para workflows de contribuidores externos.
- Coloque o secret num **environment** (`real-vpn`) com *required reviewers*. Assim, mesmo um
  workflow alterado só lê a chave depois da sua aprovação.
- Rode o agente como usuário sem privilégios, como serviço (`./svc.sh install`). Considere
  `--ephemeral` com re-registro automático, para que cada job comece numa instalação limpa.
- O runner fica fora da LAN principal (rede externa ou VLAN isolada), para não servir de ponte
  para dentro de casa.
- A regra do §4.4 vale também aqui: nenhum passo imprime o JSON. O GitHub mascara o secret no log,
  mas não dentro de arquivos enviados como artefato.

**Custo e manutenção:** os minutos de runner self-hosted não são cobrados. O custo é manter a
máquina ligada, o agente atualizado (ele se atualiza sozinho, mas para de funcionar se ficar
desligado por muito tempo), o celular carregado e a depuração USB autorizada depois de
atualizações do Android.

**Alternativa mais simples para começar:** o mesmo teste rodado à mão, na máquina de quem tem o QR
(`CAMERAS_E2E_QR_FILE=... ./gradlew :app:connectedE2eReleaseAndroidTest ...`), com o celular
plugado. O runner self-hosted só automatiza isso. Vale a pena quando o smoke deixar de ser
lembrado antes de cada release.

---

## 9. Ordem sugerida

1. **Infra mínima:** rodar o `ExampleInstrumentedTest` num emulador local (API 34, `google_apis`
   ou ATD) com `./gradlew :app:connectedDebugAndroidTest`.
2. **`testTag` + nível A:** tags por feature e testes de `ConfigScreen`, `StreamURLsScreen` e
   `EmptyStreamsScreen`. É a primeira entrega útil.
3. **CI com GMD** rodando o nível A, como job não obrigatório. Feedback cedo, antes de a suíte
   crescer.
4. **Hilt Testing + fakes + Orchestrator**, e então os cenários B #1–#6.
5. **Cenários B #7–#12** (rotação, ciclo de vida, falhas, reordenação).
6. **Nível C:** UI Automator para os diálogos; o teste de decodificação dos PNGs (com os PNGs
   regerados); depois, o smoke real rodado à mão com o QR real, contra o release.
7. **(Futuro) Runner self-hosted**, se o smoke manual deixar de ser rodado antes de cada
   release: máquina e celular dedicados, workflow `[E2E] REAL VPN SMOKE` (§8.2).
8. **Documentação:** atualizar o `docs/architecture.md` (exigência do `CLAUDE.md`): §2 e §8.2
   (novas dependências), §9.2 (fechar a nota sobre o stub), §10 (os três níveis, fakes, convenção
   de `testTag`) e §10.1 (o novo job e por que `@RealEnvironment` fica fora dele). Também revisar
   o KDoc do `jacocoTestReport` em `app/build.gradle.kts`, que hoje diz que `androidTest` "holds
   no test of its own yet".

---

## 10. Decisões

### Tomadas

| Decisão | Escolha |
|---|---|
| Dados de QR para leitura | Fixtures fictícias de `qr-code-gen/examples/` (JSON nos fluxos, PNG no teste de decodificação) |
| QR para testes de VPN | QR real fornecido por quem roda o teste; na esteira, num secret de environment, de um peer exclusivo |
| Como injetar o QR nos fluxos | `savedStateHandle` |
| Payload de QR no logcat | Removido (§4.4) |
| Onde roda o smoke com VPN real | Só à mão, na máquina de quem tem o QR. Runner self-hosted adiado |

### Em aberto

| Decisão | Opções | Recomendação |
|---|---|---|
| Rede do runner self-hosted (quando vier) | Rede externa vs. VLAN isolada em casa | Rede externa (cobre o caminho real do ISP) |
| Como cobrir o consentimento de VPN | Só UI Automator vs. extrair a criação do `Intent` para uma interface | Extrair: é uma mudança pequena e torna o caminho testável no nível B |
| Verificar texto de toast? | Validar só o estado vs. `ToastPresenter` injetável | Validar o estado primeiro |
| Isolamento do DataStore | Orchestrator vs. `StorageModule` de teste | Orchestrator primeiro |
| Job de CI obrigatório? | Sim desde o início vs. após estabilizar | Após ~2 semanas verde |
| Smoke real em release | `testBuildType = "release"` vs. build type `benchmark` | Build type derivado, assinado com a debug key, para não mexer no release |
