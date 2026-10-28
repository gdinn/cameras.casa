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

## 4. Estado global persistente sem costura

Os DataStores são delegates `by dataStore(...)`, um por processo, com nome de arquivo fixo e chave
no AndroidKeyStore. Não dá para isolar o estado de cada teste por injeção. Há dois caminhos:

- recomeçar o processo (Orchestrator com `clearPackageData`), que é mais lento;
- expor o serializer e o alias para um `StorageModule` de teste.

## 5. Efeitos ligados ao ciclo de vida

- A Home conecta a VPN no `ON_RESUME` e desconecta no `ON_PAUSE` do `NavBackStackEntry`. Navegar
  para o Config também derruba e religa o túnel. Os testes precisam contar com isso.
- O `VpnLifecycleService` desconecta no `onDestroy`/`onTaskRemoved`. Isso só é testável com o
  service real, então os fakes o evitam.
- O `InitViewModel` fica vivo na pilha e continua coletando credenciais depois que a tela saiu.

## 6. Trabalho fora da main thread que o Compose Test não espera

`ConnectVpnUseCase` e `DisconnectVpnUseCase` rodam em `Dispatchers.Default`, o DataStore grava em
`IO` e o `WhepConnectionManager` tem escopo próprio. A sincronização do Compose não espera por nada
disso, então os testes precisam de `waitUntil` e de contadores atômicos nos fakes. Não há
`IdlingResource` nem dispatcher injetável.

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
estratégia de teste, não com refatoração.
