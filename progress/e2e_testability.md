# Testabilidade instrumentada do Cameras App: o que dificulta e por quê

Análise de como a construção do app dificulta os testes instrumentados. Baseada na leitura do código
feita para o `e2e_level_b_plan.md`.

A base ajuda: o padrão Route/Screen deixa as telas puras, e quase toda integração externa passa por
uma interface injetada. Os problemas estão nos pontos onde o código pula essa regra ou depende do
sistema. Em ordem de impacto:

---

## 1. Plataforma chamada direto na UI, fora das interfaces injetadas

É o problema principal. Em alguns lugares a UI chama a plataforma sem passar pelo Hilt, e aí o fake
não alcança:

- **`ConfigRoute`** chama `VpnService.prepare(context)` direto, embora já exista o
  `VpnRepository.getVpnPermissionIntent()`. Mesmo com o repositório falso, quem abre é o diálogo
  real do sistema.
- **`QrCodeScreen`** cria o `ProcessCameraProvider` e o `QrCodeAnalyzer` dentro do composable. Não
  há como trocar a câmera, então o QR só entra por fora, pelo `savedStateHandle`.
- **`ToastDisplayer`** chama `Toast.makeText`. O toast é uma janela do sistema que o Compose Test
  não enxerga, então a mensagem não pode ser verificada, só o efeito.

### Propostas

O Hilt não injeta em composables. Para a UI, a costura é um `CompositionLocal` cujo padrão é a
implementação de produção. O `MainActivity` não muda, e não entra módulo Hilt nem `@TestInstallIn`.

#### 1a. Consentimento de VPN: uma fonte só, e o evento carrega o `Intent` (resolve também o item 2)

Hoje as três leituras do item 2 chamam `VpnService.prepare` por caminhos diferentes. A proposta
junta as duas perguntas ("já tem consentimento?" e "qual diálogo abrir?") num único valor de
domínio, com um único dono, o `VpnPermissionRepository`. O `VpnRepository` fica só com o túnel.

```kotlin
// core/permission/domain
sealed interface VpnConsent {
  data object Granted : VpnConsent
  data class Required(val intent: Intent) : VpnConsent
}

interface VpnPermissionRepository {
  fun getVpnConsent(): VpnConsent
}

// core/permission/data
override fun getVpnConsent(): VpnConsent =
  VpnService.prepare(context)?.let(VpnConsent::Required) ?: VpnConsent.Granted
```

- O `HasVpnPermissionUseCase` passa a ser `getVpnConsent() is Granted`. O
  `RequestVpnPermissionUseCase` sai de `core/vpn` para `core/permission` e devolve o `VpnConsent`.
  O `VpnRepository.getVpnPermissionIntent()` é removido.
- O `ConfigViewModel.acceptVpnPermission()` faz um `when`: `Granted` mostra o toast de "já
  concedida", e `Required` envia `VpnPermissionUiEvent.RequestPermission(intent)`. O route só
  lança esse `Intent` e não importa mais o `VpnService`.
- No teste, um único fake não consegue gerar um estado incoerente:

  ```kotlin
  class FakeVpnPermissionRepository : VpnPermissionRepository {
    @Volatile var granted = false
    override fun getVpnConsent() =
      if (granted) VpnConsent.Granted else VpnConsent.Required(Intent(FAKE_VPN_CONSENT_ACTION))
  }
  ```

  O Espresso-Intents responde à `FAKE_VPN_CONSENT_ACTION` com `RESULT_OK` ou `RESULT_CANCELED`.
  Assim o nível B testa o aceite e a recusa sem diálogo do sistema e sem `appops`.
- Por que um tipo selado e não `Intent?`: o `null` com o significado de "já concedido" é o
  contrato do `prepare` e hoje só aparece em comentários. O `VpnConsent` deixa isso explícito e
  obriga o `when` a tratar os dois casos.
- Custo: um `Intent` (um `Parcelable`, sem `Context`) passa pelo ViewModel. O domínio de
  `permission` passa a importar `android.content.Intent`, como o de `vpn` já fazia. O
  `LayerDependencyTest` só proíbe `androidx.compose` no domínio.
- Extra fora do escopo: o consentimento pode ser revogado fora do app, quando o usuário o concede a
  outro app de VPN. Com a fonte única, um `refreshVpnPermissionState()` no `ON_RESUME`, igual ao da
  câmera, resolveria. Muda comportamento, então fica separado.

#### 1b. Câmera do QR: `QrCodeScanner` por `CompositionLocal`

```kotlin
interface QrCodeScanner {
  @Composable
  fun Preview(modifier: Modifier, onQrCodeScanned: (String) -> Unit, onCameraInitError: (Throwable) -> Unit)
}

object CameraXQrCodeScanner : QrCodeScanner { /* o QrCodeCameraPreview atual */ }

val LocalQrCodeScanner = staticCompositionLocalOf<QrCodeScanner> { CameraXQrCodeScanner }
```

- O `QrCodeScreen` chama `LocalQrCodeScanner.current.Preview(...)`. O teste fornece um
  `FakeQrCodeScanner(rawJson)` que emite o JSON num `LaunchedEffect` ou por um botão de teste.
- O QR percorre o caminho real: `QrCodeRoute` → `QrCodeViewModel` → `savedStateHandle` →
  `ConfigRoute`. O teste não precisa mais gravar no `savedStateHandle` por fora, o que alivia parte
  do item 3.

#### 1c. Toast: `ToastPresenter` por `CompositionLocal`

```kotlin
fun interface ToastPresenter { fun show(@StringRes resId: Int) }

val LocalToastPresenter = staticCompositionLocalOf<ToastPresenter?> { null }
```

- `null` significa o `Toast` da plataforma. O teste fornece um `RecordingToastPresenter` e verifica
  o `resId` (`waitUntil { R.string.x in presenter.shown }`), o que também tira a dependência do
  idioma (item 9).
- Resolve a maior parte do item 7 sem mudar a UX. As cinco telas que usam o `ToastDisplayer` não
  mudam.
- Alternativa descartada: trocar o toast por `Snackbar`. O Compose Test enxerga, mas muda a UX sem
  necessidade.

#### Ordem

1c (o menor, usado em todas as telas), depois 1a com o item 2 (o único que toca um ViewModel),
depois 1b (o maior, move o `QrCodeCameraPreview` para um objeto).

#### Resultado

- **1c — feito.** O `ToastPresenter` e o `LocalToastPresenter` ficam em
  `core/components/ToastDisplayer.kt`. O `RecordingToastPresenter` fica em `androidTest/testing/`, e
  o `ToastDisplayerTest` prova que os eventos chegam ao presenter fornecido, na ordem. Sem desvio do
  plano. A única diferença é que o `LaunchedEffect` agora também usa o presenter como chave.
- **1a + item 2 — feito.** Como proposto:
  - `VpnConsent` em `core/permission/domain`;
  - `VpnPermissionRepository.getVpnConsent()` como o único `VpnService.prepare` do app;
  - o `RequestVpnPermissionUseCase` (e o seu teste) movido para `core/permission`;
  - o `getVpnPermissionIntent()` removido do `VpnRepository`;
  - o `Intent` no evento, e o route sem `VpnService` nem `LocalContext`.

  Os 289 testes unitários passam e os instrumentados compilam. Não rodei os instrumentados de novo,
  porque nenhum deles passa pelo `ConfigRoute` ou pelo `ConfigViewModel`. O `lintDebug` acusa 3 erros
  que já existiam antes da mudança: `RestrictedApi` no `NavigationRoot` e dois
  `FlowOperatorInvokedInComposition` no `ConfigRoute`, nos `filterIsInstance`/`map` dos eventos. O
  `FakeVpnPermissionRepository` ainda não existe e entra com o nível B.
- **1b — feito.** Mudanças:
  - o contrato `QrCodeScanner` e o `LocalQrCodeScanner` ficam em
    `feature/qrcode/components/QrCodeScanner.kt`;
  - o antigo `QrCodeCameraPreview` virou o `CameraXQrCodeScanner`, com o código sem mudança.

  Desvio da proposta: quem lê o local é o `QrCodeRoute`, que passa o scanner ao `QrCodeScreen` como
  parâmetro. É a mesma divisão do `LocalEglBase`: o route lê o ambiente, e a tela fica pura.

  O `FakeQrCodeScanner` (`androidTest/testing/`) emite o payload ou um erro ao ser composto. O
  `QrCodeRouteTest` (2 testes) usa o route e o `QrCodeViewModel` reais, e cobre:
  - a leitura chegando ao `onCodeScanned`;
  - o erro de câmera gerando o toast `CAMERA_INIT_ERROR` (verificado pelo `RecordingToastPresenter`
    do 1c) e voltando de tela.

  Resultado: 289 unitários e 34 instrumentados passando no emulador.

  Não testei a câmera real à mão depois da mudança. Ainda falta o caminho até o `ConfigRoute` pelo
  `savedStateHandle`, que depende do item 3 (`CamerasAppContent`).

## 2. Mesma informação lida em vários lugares

O consentimento de VPN é lido em três pontos:

- o `VpnPermissionRepository` (estado inicial do passo);
- o `VpnRepository.getVpnPermissionIntent()` (decisão no clique);
- o `prepare` do route.

Fakes independentes geram combinações impossíveis em produção. Por isso o plano do nível B usa as
permissões reais e controla o estado do sistema.

**Resolvido junto com o 1a:** as três leituras viraram uma só, o
`VpnPermissionRepository.getVpnConsent()`.

## 3. Composition root fechado no `MainActivity`

O `NavController` é criado dentro do `setContent` e não fica acessível. O teste não consegue gravar
o QR no `savedStateHandle` nem ler a pilha. Por isso o plano do nível B extrai o `CamerasAppContent`
e cria a `HiltTestActivity`.

**Resultado — feito**, como nos §3.2 e §3.3 do `e2e_level_b_plan.md`:

- o `CamerasAppContent(eglBase, navController)` está em `MainActivity.kt`, que só o chama com
  `rememberNavController()`;
- a `HiltTestActivity` está no source set `debug`, com o mesmo tema e `configChanges` do
  `MainActivity`, e há um comentário nos dois manifests pedindo que andem juntos.

Verificação:
- 289 testes unitários passam;
- 35 testes instrumentados passam no emulador;
- o `assembleRelease` passa, e a activity não está no manifest de release;
- o `MainActivity` real abre no Config sem mudança visível.

Desvio: entrou um smoke, o `CamerasAppContentTest`. Ele monta a raiz na `HiltTestActivity` com um
`NavController` do teste e espera sair do `Loading`. Como ainda não há o runner Hilt (§3.1 do plano),
o `EglBase` é criado no teste e o grafo é o real. Por isso o teste aceita tanto o Config quanto a
Home: depende das credenciais que já estão no dispositivo, até o `clearPackageData` entrar.

**Atualização:** com o §3.1 feito, o teste virou `@HiltAndroidTest`. Agora o `EglBase` é injetado, e
o `clearPackageData` garante o Config. A asserção passou a ser exata: Config com o `Loading`
embaixo.

## 4. Estado global persistente sem costura

Os DataStores são delegates `by dataStore(...)`, um por processo, com nome de arquivo fixo e chave
no AndroidKeyStore. Não dá para isolar o estado de cada teste por injeção. Há dois caminhos:

- recomeçar o processo (Orchestrator com `clearPackageData`), que é mais lento;
- expor o serializer e o alias para um `StorageModule` de teste.

Correção da análise: o `StorageModule` já fornece os dois `DataStore` pelo Hilt, então a injeção
existe. O que faltava era como construí-los. O serializer, o arquivo e o alias estavam escondidos
nos delegates.

**Resultado — feito pelo segundo caminho** (escolha do usuário). Mudanças:

- O `userPreferencesSerializer(crypto)` e o `streamPreferencesSerializer(crypto)` ficam em
  `core/storage/data/PreferencesSerializers.kt`, como `internal`. Os nomes de arquivo e os aliases
  viraram constantes ao lado de cada delegate.
- Desvio do preview: as factories recebem um `CryptoEngine` e não o alias. O `KeystoreCryptoEngine`
  abre o AndroidKeyStore no construtor, então receber o engine deixa o teste escolher entre o
  Keystore real com outro alias e um engine falso no JVM.
- Segundo desvio: as factories não ficam no mesmo arquivo dos delegates. Chamar qualquer função do
  `DataStoreKt` inicializa o delegate e o seu `KeystoreCryptoEngine`, e no JVM isso falha com
  `ExceptionInInitializerError`. Descobri isso quando o primeiro teste unitário falhou.

Testes:
- `PreferencesSerializersTest` (unitário, 3 testes) fixa os nomes e aliases já publicados e faz a
  ida e volta de cada serializer com um engine reversível.
- `IsolatedPreferencesStoreTest` (instrumentado) cria um store pela costura, com arquivo e alias
  próprios e o Keystore real. Prova que ele começa vazio, que criptografa (o texto puro não aparece
  no arquivo) e que o arquivo de produção não muda. Ao final, apaga o arquivo e a chave.

Verificação: 292 unitários e o `jacocoTestCoverageVerification` passam, e os 36 instrumentados
passam no emulador.

O que falta: o `StorageModule` de teste com `@TestInstallIn`, que precisa do runner Hilt (§3.1 do
`e2e_level_b_plan.md`). Com ele, cada teste ganha um `SingletonComponent` novo e, portanto, um
store novo. Sem o Orchestrator, as permissões e os app ops continuam sem reset, e o plano do nível B
conta com isso para a câmera (achado #5).

**Atualização — o que faltava está feito.** Com o §3.1 pronto:

- O `TestStorageModule` (`androidTest/testing/`) substitui o `StorageModule` em todo
  `@HiltAndroidTest`. Ele cria os dois stores pelas factories, em arquivos sob
  `cacheDir/test-stores/<uuid>/`, com aliases `test-` separados dos de produção.
- O alias é fixo e só o arquivo muda a cada teste. Assim não se cria uma chave nova no Keystore por
  teste, e o isolamento vem do arquivo.
- Um `TestStoreFiles` injetável expõe os arquivos do teste atual.
- O `TestStorageModuleTest` prova que os repositórios de produção começam vazios, gravam nos
  arquivos do teste e nunca criam os de produção.
- O `CamerasAppContentTest` passou a usar os stores de teste, sem mudar o código, e teve o KDoc
  ajustado.

Verificação: 292 unitários passam, e os 37 instrumentados passam no emulador (141 s).

Agora são duas camadas: o Hilt isola o storage por teste, e o Orchestrator continua resetando a
permissão de câmera (achado #5). O app op da VPN não é resetado por nenhuma das duas (§3.1 do
plano).

## 5. Efeitos ligados ao ciclo de vida

- A Home conecta a VPN no `ON_RESUME` e desconecta no `ON_PAUSE` do `NavBackStackEntry`. Navegar
  para o Config também derruba e religa o túnel. Os testes precisam contar com isso.
- O `VpnLifecycleService` desconecta no `onDestroy`/`onTaskRemoved`. Isso só é testável com o
  service real, então os fakes o evitam.
- O `InitViewModel` fica vivo na pilha e continua coletando credenciais depois que a tela saiu.

### Propostas

O comportamento está certo: o túnel só existe com a Home em primeiro plano, e o `Loading` fica na
pilha. As propostas não mudam o que o usuário vê. Elas tornam o comportamento garantido e
verificável.

#### 5a. `InitViewModel` decide uma vez

Hoje o `init` faz `collect` contínuo das credenciais. Depois que o Config abre, o `Loading` sai da
composição, mas o ViewModel fica na pilha. Depois de um QR válido, ele fica preso num
`send(ToHome)` sem receptor até o `navigateToHome` tirar o `Loading` da pilha.

- Trocar por `first { it is Loaded }`. A navegação é a mesma, e o ViewModel para de observar as
  credenciais depois da decisão.
- Teste unitário: o fluxo `Loaded(false)`, `Loaded(true)` gera só `ToConfig`.
- O `Loading` continua na pilha, então o `CamerasAppContentTest` não muda.

#### 5b. Home: o último estado de ciclo de vida sempre vence

Hoje cada `ON_RESUME`/`ON_PAUSE` dispara um `launch` próprio, e os use cases rodam em
`Dispatchers.Default`. Nada coordena as chamadas ao `GoBackend`. Num Home → Config → Home rápido, o
disconnect pode chegar depois do connect, e a Home fica sem túnel.

- A route só informa o estado: `onResumed()` / `onPaused()`.
- O ViewModel guarda a intenção num `MutableStateFlow`, e um único coletor aplica
  `connect`/`disconnect` em sequência.
- O `StateFlow` descarta os estados intermediários e repetidos. As chamadas nunca se sobrepõem, e o
  estado final é sempre o do ciclo de vida.
- Sem `collectLatest`, porque ele cancelaria um `setState` nativo no meio. As chamadas aos use
  cases rodam em `NonCancellable`: uma operação que começou termina, mesmo que o ViewModel seja
  limpo.
- Teste unitário: um connect que suspende, e a sequência resume → pause → resume. Nunca há duas
  chamadas ao mesmo tempo, e a última operação é um connect.
- Risco a conferir: a Home sai da pilha (saída do app) enquanto um connect está em andamento. O
  `ON_PAUSE` chega, mas o coletor é cancelado no `onCleared` antes de aplicar o disconnect.

#### 5c. `VpnLifecycleService`: casca fina e lógica testável

- O "derrubar o túnel" vira uma classe pequena e injetável, a `VpnTeardown`. Ela é idempotente:
  hoje o `onTaskRemoved` e depois o `onDestroy` desconectam duas vezes. Ela também não deixa uma
  falha do disconnect derrubar o app: hoje a exceção escapa de um `launch` sem handler.
- O service só a chama.
- Testes:
  - a `VpnTeardown` no JVM;
  - um instrumentado que sobe o service real com um `VpnRepository` falso (`@TestInstallIn`) e
    prova que parar o service chama o disconnect.
- O `onTaskRemoved` de verdade (arrastar o app nos recentes) continua só no teste manual.

#### Ordem

5a (o menor), depois 5b, depois 5c (o único que mexe na infraestrutura instrumentada).

#### Resultado

- **5a — feito.** O `init` do `InitViewModel` pega o primeiro `Loaded` e decide.
  - Desvio: `firstOrNull()` em vez de `first()`. Um fluxo que termina sem `Loaded` lançaria uma
    exceção no `viewModelScope` e derrubaria o app. Em produção o DataStore nunca termina, mas o
    teste de `Loading` já existente usa um fluxo finito e pegou isso.
  - O teste novo (`decides once and ignores later credential changes`) falha no código antigo (chega
    um `ToHome` a mais) e passa no novo.
- **5b — feito.** O `HomeRoute` chama `onResumed()`/`onPaused()`, e um coletor único aplica a
  intenção em `NonCancellable`.
  - O risco se confirmou. Com a Home pausando durante um connect e o ViewModel limpo em seguida, o
    disconnect nunca rodava (o teste falhou com `[connect]`). Correção: o `finally` do coletor
    desconecta quando a última operação foi um connect. A Home é a única dona do túnel.
  - Desvio: o `ToConfig` de falha é enviado num `launch` à parte. O canal é rendezvous, e o `send`
    direto prenderia o coletor do túnel até a route coletar o evento.
  - Desvio: não injetei o dispatcher nos `Connect/DisconnectVpnUseCase`. Os testes do ViewModel
    usam mocks dos use cases, então não precisaram, e isso fica para o item 6.
  - Testes: os três de `connectVpn` viraram de `onResumed`, e o de `disconnectVpn` deu lugar a
    cinco novos:
    - pause depois de resume;
    - pause antes de qualquer resume;
    - pause durante o connect (aplicado depois, concorrência máxima 1);
    - pause e resume rápidos (só um connect);
    - ViewModel limpo durante o connect.
  - Resultado: 297 testes unitários passam.
- **5c — feito.** A `VpnTeardown` fica em `core/vpn/data`, e o service só a chama.
  - Desvio: a idempotência marca o sucesso, não a chamada. Depois de uma falha, a próxima chamada
    tenta de novo (o `onDestroy` depois de um `onTaskRemoved` que falhou), e um `Mutex` impede duas
    ao mesmo tempo.
  - O `VpnTeardownTest` (JVM, 3 testes) cobre o disconnect, o segundo `invoke` sem efeito e a falha
    que não lança e é refeita.
  - O `FakeVpnRepository`, o `FakeVpnLifecycleController` e o `TestVpnModule` (`@TestInstallIn`
    no lugar do `VpnModule`) entraram agora, em `androidTest/testing/`, adiantando o nível B.
    Nenhum `@HiltAndroidTest` sobe mais o WireGuard, e o KDoc do `CamerasAppContentTest` foi
    ajustado.
  - O `VpnLifecycleServiceTest` sobe o service real pelo `startService`/`stopService` e espera o
    `vpnState` do fake chegar a `DISCONNECTED`, sem `sleep`.
  - Desvio: sem `ServiceTestRule`. O `startService` dela também faz bind e espera a conexão, e o
    `onBind` do service devolve `null`.
  - Conferi que o teste falha (timeout) com o `vpnTeardown()` removido do `onDestroy`.

  Verificação: 300 testes unitários e o `jacocoTestCoverageVerification` passam, e os 38
  instrumentados passam no emulador (163 s). O usuário testou o app real à mão depois das três
  mudanças, e ele funcionou corretamente.

## 6. Trabalho fora da main thread que o Compose Test não espera

`ConnectVpnUseCase` e `DisconnectVpnUseCase` rodam em `Dispatchers.Default`, o DataStore grava em
`IO` e o `WhepConnectionManager` tem escopo próprio. A sincronização do Compose não espera por nada
disso, então os testes precisam de `waitUntil` e de contadores atômicos nos fakes. Não há
`IdlingResource` nem dispatcher injetável.

### Proposta

Injetar os dispatchers e, no teste instrumentado, trocá-los por dispatchers que o Espresso enxerga.
O Compose Test sincroniza pelo Espresso, então passa a esperar esse trabalho como espera a main
thread.

- `core/coroutines`: os qualifiers `@IoDispatcher` e `@DefaultDispatcher`, e o `DispatchersModule`
  com `Dispatchers.IO`/`Default`. Ficam na raiz do pacote porque os use cases do `domain` os
  importam.
- Quem injeta: `ConnectVpnUseCase`, `DisconnectVpnUseCase`, `VpnRepositoryImpl`,
  `VpnLifecycleService` e `WhepRemoteDataSourceImpl`. Os serializers do storage recebem um
  parâmetro `ioDispatcher` com padrão `Dispatchers.IO`, porque os stores de produção são delegates
  `by dataStore`, fora do Hilt.
- O `Dispatchers.Main` fica fixo. A main thread já é sincronizada, e nos unitários o
  `MainDispatcherRule` a troca.
- No teste: o `IdlingDispatcher` embrulha o dispatcher real e é um `IdlingResource`, ocupado
  enquanto houver bloco na fila ou rodando. O `TestDispatchersModule` (`@TestInstallIn`) entrega os
  dois `TestDispatchers`, e o `HiltTestRunner` os registra no `IdlingRegistry` uma vez por processo.
  O `TestStorageModule` passa o IO injetado ao `scope` do DataStore e ao serializer.
- Limite: o `IdlingDispatcher` conta blocos, não corrotinas. O que suspende fora dele (um `delay`,
  um callback nativo do WebRTC, um dispatcher não embrulhado) não é esperado e continua precisando
  de `waitUntil`. A troca de um dispatcher contado para outro, ou para a main, não abre brecha,
  porque o próximo bloco entra na fila antes do atual terminar.

### Resultado

- **Feito**, como proposto. Os unitários de `Connect/DisconnectVpnUseCase` recebem um
  `StandardTestDispatcher` e ganharam um teste cada, que prova que o repositório roda no dispatcher
  injetado. O `WhepRemoteDataSourceImplTest` passa `Dispatchers.IO`, porque faz rede de verdade no
  `MockWebServer`.
- `IdlingDispatcherTest` (instrumentado, 2 testes):
  - o dispatcher fica ocupado enquanto o bloco roda e avisa a volta ao idle;
  - um `LaunchedEffect` vai da main ao `default`, ao `io` e volta, e o `onNodeWithText("done")`
    passa sem `waitUntil`.
- O `CamerasAppContentTest` trocou o `waitUntil` por `runOnIdle`: a leitura das credenciais roda no
  IO contado, então idle já significa que o app navegou para o Config.
- Os `waitUntil` que sobraram (`QrCodeRouteTest`, `ToastDisplayerTest`) não dependem desses
  dispatchers e ficaram como estavam.
- Verificação (rodada pelo usuário, porque o sandbox desta sessão bloqueia o socket local do Gradle
  e do `adb`):
  - os testes unitários e o `jacocoTestCoverageVerification` passam;
  - os 40 instrumentados passam no `Pixel_8a` (157 s), inclusive o `CamerasAppContentTest` sem
    `waitUntil`.
- Os qualifiers nos parâmetros de construtor geram o warning do KT-73255 (alvo padrão da
  anotação), igual ao `@ApplicationContext` e ao `@UserPreferencesStore` que já existiam. Ficaram
  assim por consistência.

## 7. Feedback só por toast, sem estado observável

Vários resultados aparecem só como toast: QR lido com sucesso, consentimento aceito, falha de
stream, falha ao salvar e o "aperte back de novo". A falha de stream é o caso mais claro: não muda
nada no `HomeUiState`, então o teste só consegue provar que o app não caiu. Um `ToastPresenter`
injetável ou um estado de erro no card resolveriam.

## 8. Componentes nativos e gestos

- O `WebRtcVideoPlayer` cria um `SurfaceViewRenderer` com o `EglBase` real. Funciona no emulador,
  mas é código nativo, e o `onFirstFrameRendered` está vazio. Não existe sinal de "vídeo tocando"
  para o nível C.
- Reordenar e paginar o grid fixo usa um gesto escrito à mão no container (passe `Initial`). O modo
  dinâmico usa uma biblioteca. O "Reconfigurar" do modo dinâmico só aparece depois de um overscroll
  de uma tela inteira. São todos testes de gesto, os mais instáveis.

## 9. Semântica e localização

Até o nível A não havia nenhum `testTag`, e a Home ainda não tem. O estado dos passos era só ícone e
cor (hoje é `stateDescription`). Os textos mudam entre `en` e `pt-BR`, então procurar por texto
amarra o teste ao idioma.

## 10. O que depende do mundo real

A VPN de verdade precisa de IPv6, do endpoint de casa, do Pi com vídeo e de um peer exclusivo. O R8
só falha em runtime, então o teste mais importante para release (QR → túnel → frame) não roda em CI
hospedado e precisa do build minificado num dispositivo físico.

---

## Conclusão

Os itens 1 a 3 são os que mais pesam, porque são dívidas de projeto e não limitações da plataforma.
As correções são pequenas:

- o consentimento de VPN com uma fonte só, e o `Intent` no evento do ViewModel (1a + item 2, feito);
- um `QrCodeScanner` trocável por `CompositionLocal` (1b, feito);
- um `ToastPresenter` trocável por `CompositionLocal` (1c, feito);
- a raiz de composição separada da activity (item 3, feito).

O resto (lifecycle, threads, nativo, VPN real) é inerente ao que o app faz e se resolve com a
estratégia de teste, não com refatoração. O lifecycle (item 5) não mudou de comportamento, mas
ficou garantido e testável: o gate decide uma vez, a Home serializa o túnel, e o service delega a
uma `VpnTeardown` testável no JVM. As threads (item 6) ganharam dispatchers injetáveis, e nos
testes instrumentados o Compose Test espera o trabalho fora da main thread sem `waitUntil`.
