# Relatório da branch `feat/e2e_tests`

Relatório commit a commit da branch `feat/e2e_tests` em relação à `main`. Para cada commit de
código: **como era antes**, **por que mudou** (com os fundamentos de programação e de testabilidade
envolvidos) e **como ficou depois**. Os commits só de documentação aparecem resumidos, junto da fase
a que pertencem.

Números da branch: **37 commits**, 109 arquivos, cerca de +6.200 / −470 linhas. São 25 commits que
mexem em código e 12 só de documentação.

Documentos de origem, todos nesta pasta:

- `e2e_research.md`: a pesquisa que define os níveis A, B e C;
- `e2e_level_a_plan.md`: o plano do nível A (telas isoladas), concluído;
- `e2e_level_b_plan.md`: o plano do nível B (fluxos herméticos com Hilt), com a infraestrutura
  pronta e os cenários ainda por escrever;
- `e2e_testability.md`: a análise do que, na construção do app, dificultava os testes
  instrumentados, e as correções feitas para cada item.

---

## Sumário

- [0. Fundamentos usados no relatório](#0-fundamentos-usados-no-relatório)
- [1. Fase 0: segurança antes de tudo](#1-fase-0-segurança-antes-de-tudo)
- [2. Fase 1: nível A, telas isoladas](#2-fase-1-nível-a-telas-isoladas)
- [3. Fase 2: costuras para o que a UI chamava direto](#3-fase-2-costuras-para-o-que-a-ui-chamava-direto)
- [4. Fase 3: infraestrutura do nível B (Hilt, Orchestrator, storage isolado)](#4-fase-3-infraestrutura-do-nível-b-hilt-orchestrator-storage-isolado)
- [5. Fase 4: ciclo de vida e threads](#5-fase-4-ciclo-de-vida-e-threads)
- [6. Fase 5: Home observável](#6-fase-5-home-observável)
- [7. Fase 6: smoke com o ambiente real (nível C, fase 1)](#7-fase-6-smoke-com-o-ambiente-real-nível-c-fase-1)
- [8. Resumo](#8-resumo)

---

## 0. Fundamentos usados no relatório

Conceitos que se repetem ao longo do texto. Vale ler antes das fases.

### 0.1 Testes unitários e testes instrumentados

| | Unitário (`src/test`) | Instrumentado (`src/androidTest`) |
|---|---|---|
| Onde roda | JVM do computador | Num dispositivo ou emulador Android |
| O que tem | Kotlin puro, mocks (MockK), `TestDispatcher` | Framework Android real: `Activity`, Compose, Keystore, WebRTC nativo |
| Custo | Milissegundos | Segundos por teste, mais instalação de APK |
| Cobre | Lógica (ViewModels, use cases, regras) | Integração com a plataforma, a UI e o que o usuário vê |

O app já tinha uma suíte unitária grande (cerca de 290 testes, com JaCoCo e Konsist). O
`src/androidTest` tinha só o `ExampleInstrumentedTest` gerado pelo template. Esta branch constrói a
suíte instrumentada do zero.

### 0.2 Os três níveis do `e2e_research.md`

- **Nível A, telas isoladas.** Monta um `*Screen` com um `uiState` escrito à mão e verifica o que
  ele mostra e qual callback cada interação chama. Sem Hilt, sem navegação, sem fakes.
- **Nível B, fluxos herméticos.** Usa a activity, o Hilt, a navegação, os ViewModels e o DataStore
  reais, e troca só o que sai do processo (túnel WireGuard, WebRTC/WHEP). "Hermético" quer dizer que
  o resultado não depende de rede, de servidor nem de estado deixado por outro teste.
- **Nível C, ambiente real.** QR real → túnel real → vídeo real, num aparelho físico.

É a pirâmide de testes clássica: muitos testes baratos e determinísticos na base, poucos testes
caros e frágeis no topo.

### 0.3 Árvore de semântica do Compose

O Compose Test não enxerga pixels. Ele enxerga a **árvore de semântica**, a mesma que alimenta o
TalkBack. Disso saem três consequências que aparecem no relatório todo:

1. **`testTag`** é uma propriedade semântica que só existe para o teste achar o nó
   (`onNodeWithTag`). Procurar por texto amarra o teste ao idioma, já que o app tem `values-en` e
   `values-pt-rBR`.
2. **Árvore mesclada.** Um nó `clickable` mescla (`mergeDescendants`) os filhos: título, subtítulo e
   ícone viram um nó só. Por padrão o teste consulta a árvore mesclada. Para achar um filho tagueado
   dentro de um nó mesclado, ele usa `useUnmergedTree = true`.
3. **O que não está na semântica não pode ser afirmado.** Um ícone com `contentDescription = ""` é
   invisível para o teste e para o TalkBack. Testabilidade e acessibilidade andam juntas: o que
   ajuda uma quase sempre ajuda a outra.

### 0.4 Costura (seam)

O termo é de Michael Feathers (*Working Effectively with Legacy Code*): um ponto onde dá para
**trocar um comportamento sem editar o código no lugar**. Em Android, as costuras usuais são:

- **Injeção de dependência (Hilt).** O código pede uma interface, e o teste troca a implementação
  com `@TestInstallIn`.
- **`CompositionLocal`.** O Hilt não injeta em composables. Para a UI, a costura é um
  `CompositionLocal` cujo valor padrão é a implementação de produção, e o teste fornece outra com
  `CompositionLocalProvider`.
- **Parâmetros com valor padrão.** Ex.: `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`.

O sinal de que falta uma costura é a UI chamar a plataforma direto (`VpnService.prepare`,
`Toast.makeText`, `ProcessCameraProvider`), porque aí nenhum fake alcança.

### 0.5 Fakes, mocks e gravadores

- **Mock** (MockK): objeto programado por expectativa (`every { ... } returns ...`). Bom no JVM.
- **Fake**: implementação real e simplificada da interface (`FakeVpnRepository`: o `connect` só
  muda um `StateFlow`). Bom em testes instrumentados, onde o app inteiro roda e o comportamento
  precisa ser coerente entre várias chamadas.
- **Gravador** (recording): um fake que registra as chamadas para o teste afirmar depois
  (`RecordingToastPresenter`, os `events.calls` das telas).

### 0.6 Hilt em testes

- `HiltTestApplication` substitui o `Application` do app. Quem faz a troca é um runner próprio
  (`HiltTestRunner`).
- `@HiltAndroidTest` + `HiltAndroidRule` criam um `SingletonComponent` **novo para cada teste**.
  Tudo que é `@Singleton` nasce de novo: é isso que isola o storage por teste.
- `@TestInstallIn(replaces = [Modulo::class])` troca um módulo para a suíte inteira, compilando um
  grafo só. `@UninstallModules` + `@BindValue` troca por classe de teste, mas cada uso compila um
  grafo próprio.

### 0.7 Sincronização e flakiness

Um teste é **flaky** quando às vezes passa e às vezes falha sem mudança no código. A causa mais
comum em UI é uma corrida: o teste afirma antes de o app terminar o trabalho.

- O Compose Test sincroniza automaticamente com a **main thread** e com a recomposição.
- Trabalho em `Dispatchers.IO`/`Default` **não** é esperado. As saídas são `waitUntil { ... }` com
  timeout (polling) ou um **`IdlingResource`**, que é a forma de o Espresso (e, por baixo, o Compose
  Test) saber que há trabalho pendente.
- `Thread.sleep` é proibido nas convenções da branch: é lento quando sobra tempo e flaky quando
  falta.

### 0.8 Android Test Orchestrator

Roda **cada teste num processo novo**. Com `clearPackageData`, apaga os dados do app (arquivos e
permissões runtime) entre os testes. Garante isolamento ao custo de tempo: aqui a suíte foi de 49 s
para 128 s quando ele entrou.

---

## 1. Fase 0: segurança antes de tudo

### 1. `9200110` fix: stop logging the QR code payload

**Antes.** O QR carrega a chave privada e a preshared key do WireGuard. Três lugares o logavam:

```kotlin
// QrCodeAnalyzer
Log.d("QrCodeAnalyzer", "Scanned content: $rawValue")
// QrCodeRoute
Log.d(DEBUG_TAG, "QrCodeRoute: $rawValue")
// ConfigViewModel, na falha de parse
Log.e(DEBUG_TAG, "QR Code scan error", e)
```

O terceiro é sutil: a mensagem de exceção do kotlinx.serialization **cita o trecho do JSON** que
falhou, então logar a exceção inteira vazava o payload do mesmo jeito.

**Por quê.** O logcat é legível por qualquer pessoa com `adb`, e testes instrumentados guardam o
logcat nos artefatos (relatórios, CI). Antes de criar uma suíte que vai manipular QRs (as fixtures
e, mais tarde, o QR real do nível C), era preciso garantir que o segredo não iria parar nos logs. É
o princípio de **minimizar dados sensíveis em canais de diagnóstico**: log é um canal de saída como
outro qualquer.

**Depois.** Os dois `Log.d` saíram. O `ConfigViewModel` loga só o tipo da exceção:

```kotlin
Log.e(DEBUG_TAG, "QR Code scan error: ${e::class.simpleName}")
```

A mesma regra volta no smoke real (§7): numa falha de parse, o teste reporta só o tipo.

---

## 2. Fase 1: nível A, telas isoladas

Objetivo: testar `ConfigScreen`, `StreamURLsScreen` e `EmptyStreamsScreen` como funções puras de
estado → UI. Antes de escrever os testes, a leitura do código (§1 do `e2e_level_a_plan.md`) achou
pontos que impediam o teste. Os cinco primeiros commits preparam o terreno.

### 2. `931e7c7` refactor: apply ConfigButton's modifier to the clickable row

**Antes.** O parâmetro `modifier` do `ConfigButton` era aplicado ao **círculo de status** de 30 dp,
e a `Row` externa, a clicável, começava com um `Modifier` novo:

```kotlin
Row(
  modifier = Modifier            // linha clicável: ignora o parâmetro
      .alpha(...)
      .clickable(onClick = onClicked)
  ...
) {
  ...
  Row(
    modifier = modifier          // círculo de 30 dp: recebe o parâmetro
        .size(30.dp)
```

**Por quê.** A convenção do Compose é que **o `modifier` recebido vai no nó raiz** do composable.
Quem chama espera que um `Modifier.testTag(...)`, um `padding` ou um `size` atue no componente
inteiro. Aqui, uma tag passada pelo chamador marcaria o círculo, e o `performClick()` do teste
acertaria o centro do círculo, possivelmente fora da área clicável. O bug existia em produção mas
não aparecia, porque os 8 chamadores passavam `Modifier` vazio.

**Depois.** Os dois foram trocados: o parâmetro vai na `Row` externa, e o círculo usa um `Modifier`
próprio. Não há efeito visual.

### 3. `9bfaff8` feat: expose each config step's state to accessibility

**Antes.** O estado de cada passo do Config (`Loading`, `Ready`, `Done`, `Error`) aparecia só como
ícone (com `contentDescription = ""`) ou spinner. Na semântica, não existia.

**Por quê.** Pelo §0.3, o que não está na semântica não pode ser afirmado. O teste não tinha como
dizer "o passo do QR está concluído", e o TalkBack também não anunciava nada: um usuário cego não
sabia quais passos faltavam. A solução certa é **semântica**, não uma tag extra: o
`stateDescription` existe exatamente para isso ("estado atual de um controle"), é lido pelo
TalkBack e pode ser afirmado com `SemanticsMatcher.expectValue(StateDescription, ...)`.

Uma decisão de arquitetura: o mapeamento `ConfigButtonState → @StringRes` ficou num `when` privado
**dentro do componente**, e não no `ConfigUiState`. Texto falado é apresentação. O `ConfigUiState`
é medido pelo JaCoCo no JVM, onde um `R.string` não significa nada. É a separação MVVM: o ViewModel
produz estado, a View decide como representá-lo.

**Depois.**

```kotlin
val stateDescription = stringResource(uiState.stateDescriptionRes())
Row(
  modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .clickable(onClick = onClicked)
      .semantics { this.stateDescription = stateDescription }
```

Há strings novas nos três `values` ("Loading"/"Carregando", "Pending"/"Pendente",
"Done"/"Concluído", "Error"/"Erro"). Como o `clickable` já mescla os filhos, o nó tagueado carrega
título, subtítulo e estado de uma vez.

### 4. `3d24c39` docs: explain that a disabled config step is still clickable

Commit de KDoc, mas registra uma decisão que os testes documentam. O `enabled = false` do
`ConfigButton` é **só visual** (`alpha 0.5`): o clique continua chegando ao ViewModel, que responde
com um toast explicando o que falta (ex.: `CAMERA_PERMISSION_REQUIRED_FOR_QR_CODE`). Um botão
desabilitado de verdade ignoraria o toque em silêncio. Por isso a semântica não marca o nó como
desabilitado, e o teste `qrCodeStepStillClickableWithoutCameraPermission` chama `assertIsEnabled()`
para travar esse comportamento.

### 5. `f4c4349` test: add test tags to the config, stream URLs and empty Home screens

**Antes.** Não havia nenhum `testTag` no app.

**Por quê.** Pelo §0.3, sem tag o teste procuraria por texto, e isso quebra em dois casos:

1. **Idioma.** O teste rodaria em inglês e falharia em pt-BR.
2. **Texto repetido.** "Cancel" é ao mesmo tempo o botão do diálogo e o do formulário. "Add URL" é o
   `contentDescription` do `+` e o texto do confirmar do formulário. Por texto, o teste não
   distingue os dois.

Decisões de projeto:

- **Um `object` de tags por feature** (`ConfigTestTags`, `StreamURLsTestTags`, `HomeTestTags`), com
  `const val`, em `main`, ao lado dos componentes. Tela e teste usam a **mesma constante**: renomear
  a tag é um erro de compilação, não um teste quebrado em runtime. O nome casa com o padrão
  `**/*Test*.*`, então os objetos já ficam fora do JaCoCo.
- **Tags por item derivadas da chave**: `urlRow(url)`, `removeUrlButton(url)`. A URL já é a `key` da
  `LazyColumn`, logo é única na tela.
- **Tags por orientação**: `gridColumnsInput(orientation)`. Para isso, o `GridOrientationBlock`
  ganhou o parâmetro `orientation`.
- O `ConfirmationDialog` é compartilhado, então duas tags (`DIALOG_CONFIRM_BUTTON` e
  `DIALOG_DISMISS_BUTTON`) cobrem os quatro diálogos.

**Depois.** As tags estão aplicadas em `ConfigScreen`, `StreamURLsScreen`, `AddStreamUrlForm`,
`StreamUrlRow`, `GridPreferencesSection` e `EmptyStreamsScreen`. A tag `LOADING` ficou declarada mas
ainda não aplicada (próximo commit).

### 6. `f4a8761` test: tag the stream URLs loading state

**Antes.** `fun LoadingScreen(textLabel: String)` não aceitava `Modifier`.

**Por quê.** Mesma convenção do `931e7c7`: **todo composable reutilizável deve aceitar
`modifier: Modifier = Modifier`**. Sem isso, o chamador não consegue nem posicionar nem marcar o
componente.

**Depois.** A assinatura passou a ser `LoadingScreen(textLabel, modifier = Modifier)`, aplicado à
`Column`. É uma mudança compatível: os três chamadores existentes não mudaram, e o
`StreamURLsScreen` passa `Modifier.testTag(StreamURLsTestTags.LOADING)`. A tag `LOADING` da Home
reaproveita o mesmo parâmetro mais tarde.

### 7. `3c6ea42` test: cover ConfigScreen, StreamURLsScreen and EmptyStreamsScreen with Compose UI tests

**Antes.** Só havia o `ExampleInstrumentedTest`.

**Por quê.** É o nível A propriamente dito. Testar a tela pura (estado → UI e interação → callback)
é o degrau mais barato e determinístico de teste de UI: sem I/O, sem threads, sem Hilt. Ele pega
regressões de layout e de fiação (o botão certo chama o callback certo) que o teste unitário do
ViewModel não enxerga.

**Depois.** 30 testes novos (10 + 18 + 2) e um helper:

```kotlin
// testing/ComposeTestSupport.kt
fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) =
  setContent { CamerasTheme { content() } }

fun string(@StringRes resId: Int): String =
  InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
```

Padrões que valem conhecer:

- **Gravador de eventos.** O `StreamURLsScreen` tem 18 callbacks. Em vez de lambdas vazias, cada
  callback faz `events.calls += "nome:arg"`, e o teste afirma a lista inteira:
  `assertEquals(listOf("remove:$SECOND_URL"), events.calls)`. Isso pega também callbacks chamados a
  mais.
- **Vários estados num `setContent` só.** O `createComposeRule` aceita um único `setContent` por
  teste. Os testes de diálogo e de validação do grid passam um `mutableStateOf(uiState)` e trocam o
  valor dentro de um loop.
- **`LazyColumn` compõe sob demanda.** A seção de grid fica abaixo da dobra e pode não existir na
  árvore. O helper `onListItem(tag)` faz `performScrollToNode(hasTestTag(tag))` antes de cada uso,
  qualquer que seja o tamanho da tela.
- **Um campo por teste.** Com o estado fixo, quando o foco passa a outro campo, o primeiro volta ao
  valor do estado e emite um `onValueChange` extra, coisa que com o ViewModel não acontece. Afirmar
  esse evento prenderia o teste a um detalhe interno do Compose.
- **Ordem por posição.** `urlsAreListedInOrder` compara o `boundsInRoot.top` das linhas.

Validação: 31 verdes no `Pixel_8a`, 3 execuções seguidas em en-US e 1 em pt-BR (essa ainda trocando
o idioma do emulador com `adb root`).

### Commits de documentação da fase

- `1722f8f`: adiciona o `e2e_research.md` (a pesquisa) e o plano do nível A.
- `746fe9f`, `362e273`, `dd1c732`: registram no plano o resultado de cada etapa.
- `7478463`: cria a pasta `progress/` com o README e move as notas para lá.
- `3aee4d6` e `cb6b729`: acrescentam ao `CLAUDE.md` as regras de "progress notes" e diretrizes de
  testes instrumentados, e depois retiram as diretrizes, que ficaram no `architecture.md`.
- `455e2fd`: documenta os testes de tela no `architecture.md` (§9.2, §10, §10.1) e ajusta o KDoc do
  `jacocoTestReport`.

---

## 3. Fase 2: costuras para o que a UI chamava direto

Commit de documentação que abre a fase: **`95ebf0f`** adiciona o plano do nível B e o
`e2e_testability.md`. Ao planejar o nível B, a leitura do código mostrou que três pontos da UI
chamavam a plataforma sem passar por nenhuma interface (item 1 do `e2e_testability.md`): nenhum fake
os alcançava. Os três commits a seguir criam as costuras que faltavam, na ordem proposta: do menor
para o maior.

### 8. `f00aebb` refactor: show toasts through a swappable ToastPresenter

**Antes.** O `ToastDisplayer`, usado por cinco telas, chamava `Toast.makeText(...).show()` direto
dentro do `LaunchedEffect`.

**Por quê.** O toast é uma **janela do sistema**, fora da hierarquia do Compose: o Compose Test não
a enxerga. Vários resultados do app aparecem *só* como toast (QR lido, consentimento aceito, falha de
stream), então nenhum teste conseguia afirmar a mensagem. Trocar por `Snackbar`, que o Compose Test
enxerga, mudaria a UX sem necessidade. A saída é uma costura por `CompositionLocal` (§0.4).

**Depois.**

```kotlin
fun interface ToastPresenter { fun show(@StringRes resId: Int) }

val LocalToastPresenter = staticCompositionLocalOf<ToastPresenter?> { null }

@Composable
fun ToastDisplayer(toastUiEvent: Flow<ToastUiEvent>) {
  val presenter = LocalToastPresenter.current ?: remember(context, resources) {
    ToastPresenter { resId -> Toast.makeText(context, resources.getString(resId), LENGTH_SHORT).show() }
  }
  LaunchedEffect(toastUiEvent, presenter) { ... presenter.show(event.resId) }
}
```

- `null` como padrão significa "o toast da plataforma". Produção não muda, e nenhuma das cinco telas
  precisou ser editada.
- No teste, o `RecordingToastPresenter` grava o **`resId`**, não o texto. A asserção fica
  independente do idioma.
- `staticCompositionLocalOf`: o valor praticamente não muda, então não vale o custo de rastrear
  leituras.
- O `ToastDisplayerTest` prova que os eventos chegam ao presenter fornecido, na ordem.

### 9. `0437aee` refactor: read the VPN consent from a single source

**Antes.** O consentimento de VPN era lido em **três lugares**, todos chamando
`VpnService.prepare(context)` por caminhos diferentes:

1. `VpnPermissionRepository.hasVpnPermission()`, para o estado inicial do passo;
2. `VpnRepository.getVpnPermissionIntent()`, para decidir no clique se precisava pedir;
3. o próprio `ConfigRoute`, que chamava `VpnService.prepare(context)` de novo para montar o `Intent`
   e lançar o diálogo.

O evento `VpnPermissionUiEvent.RequestPermission` era um `data object`, sem payload.

**Por quê.** Dois problemas, um de design e um de teste:

- **Fonte única da verdade (single source of truth).** Três leituras da mesma informação podem
  discordar. Em teste é pior: fakes independentes geram **estados impossíveis em produção** (o passo
  em `Ready` e, no clique, o toast "já concedida"). O plano do nível B chegou a desistir de fakes de
  permissão por causa disso.
- **A UI chamava a plataforma direto** (item 1 do `e2e_testability.md`). Mesmo com o repositório
  falso, o route abria o diálogo real do sistema.

A proposta junta as duas perguntas ("já tem consentimento?" e "qual diálogo abrir?") num **tipo
selado de domínio**, com um único dono:

- Por que não `Intent?`? Porque o `null` com o significado de "já concedido" é o contrato do
  `prepare`, e só aparecia em comentários. Um `sealed interface` deixa isso **explícito no tipo** e
  obriga o `when` a tratar os dois casos (exaustividade verificada pelo compilador).
- Custo aceito: um `Intent` (um `Parcelable`, sem `Context`) passa pelo ViewModel, e o domínio de
  `permission` importa `android.content.Intent`. O `LayerDependencyTest` (Konsist) só proíbe
  `androidx.compose` no domínio.

**Depois.**

```kotlin
// core/permission/domain
sealed interface VpnConsent {
  data object Granted : VpnConsent
  data class Required(val intent: Intent) : VpnConsent
}

// core/permission/data: o único VpnService.prepare do app
override fun getVpnConsent(): VpnConsent =
  VpnService.prepare(context)?.let(VpnConsent::Required) ?: VpnConsent.Granted

// ConfigViewModel
fun acceptVpnPermission() {
  when (val consent = requestVpnPermissionUseCase()) {
    VpnConsent.Granted -> showToast(ConfigToastMessage.PERMISSION_ALREADY_GRANTED)
    is VpnConsent.Required -> viewModelScope.launch {
      _vpnPermissionUiEvent.send(VpnPermissionUiEvent.RequestPermission(consent.intent))
    }
  }
}

// ConfigRoute: só lança o Intent que veio no evento
events = viewModel.vpnPermissionUiEvent
  .filterIsInstance<VpnPermissionUiEvent.RequestPermission>()
  .map { it.intent }
```

- `HasVpnPermissionUseCase` virou `getVpnConsent() is Granted`.
- `RequestVpnPermissionUseCase` saiu de `core/vpn` para `core/permission` e devolve `VpnConsent`.
- `VpnRepository.getVpnPermissionIntent()` foi removido: o `VpnRepository` fica só com o túnel
  (responsabilidade única).
- O `ConfigRoute` não importa mais `VpnService` nem `LocalContext`.
- Os testes unitários foram ajustados, e o do use case foi movido de pacote.

No nível B, um `FakeVpnPermissionRepository` vai devolver `Required(Intent(FAKE_ACTION))`, e o
Espresso-Intents vai responder `RESULT_OK` ou `RESULT_CANCELED`. Assim dá para testar o aceite e a
recusa sem diálogo do sistema. Esse fake ainda não existe.

### 10. `bd04753` refactor: make the QR scanner camera swappable

**Antes.** O `QrCodeScreen` criava o `ProcessCameraProvider`, o `PreviewView` e o `QrCodeAnalyzer`
dentro de um composable privado (`QrCodeCameraPreview`).

**Por quê.** A câmera do emulador não pode ser apontada para um QR. Sem costura, a tela do QR era
intestável, e o QR só podia entrar no fluxo "por fora", gravando no `savedStateHandle`.

**Depois.**

```kotlin
interface QrCodeScanner {
  @Composable
  fun Preview(modifier: Modifier, onQrCodeScanned: (String) -> Unit, onCameraInitError: (Throwable) -> Unit)
}

object CameraXQrCodeScanner : QrCodeScanner { /* o QrCodeCameraPreview antigo, sem mudança */ }

val LocalQrCodeScanner = staticCompositionLocalOf<QrCodeScanner> { CameraXQrCodeScanner }
```

- **Desvio da proposta, e o motivo:** quem lê o `CompositionLocal` é o `QrCodeRoute`, que passa o
  scanner ao `QrCodeScreen` **como parâmetro**. É a mesma divisão já usada com o `LocalEglBase`: o
  *route* lê o ambiente, e a *screen* continua pura, recebendo tudo por parâmetro. Assim o padrão
  Route/Screen do app se mantém.
- O `FakeQrCodeScanner` emite o payload (ou um erro) num `LaunchedEffect` assim que é composto,
  como uma câmera no primeiro frame.
- O `QrCodeRouteTest` (2 testes) usa o route e o `QrCodeViewModel` reais e cobre a leitura chegando
  ao `onCodeScanned` e o erro de câmera gerando o toast `CAMERA_INIT_ERROR` (verificado pelo
  `RecordingToastPresenter` do commit anterior) e voltando de tela.

### Commit de documentação da fase

- `4e489eb`: documenta as três costuras no `architecture.md` e registra os resultados no
  `e2e_testability.md`.

---

## 4. Fase 3: infraestrutura do nível B (Hilt, Orchestrator, storage isolado)

O nível B precisa de três coisas que o app não oferecia: alcançar o `NavController`, trocar
bindings do Hilt e começar cada teste com storage vazio.

### 11. `4065c80` refactor: extract the composition root from MainActivity

**Antes.**

```kotlin
setContent {
  CamerasTheme {
    CompositionLocalProvider(LocalEglBase provides eglBase) {
      NavigationRoot(navController = rememberNavController())
    }
  }
}
```

O `NavController` nascia dentro do `setContent` e não ficava acessível a ninguém.

**Por quê.** A **raiz de composição** (*composition root*, onde o app é montado) estava fechada na
activity. Com `createAndroidComposeRule<MainActivity>()`, o teste não alcança o `NavController`:
não consegue ler a pilha ("não há destino duplicado") nem entregar o QR pelo `savedStateHandle`.
Separar *o que* é montado de *quem* hospeda permite que o teste monte **exatamente a mesma árvore**
com um `NavController` que ele controla.

**Depois.**

- **`CamerasAppContent(eglBase, navController)`**, `internal`, no mesmo arquivo do `MainActivity`. A
  classe gerada `MainActivityKt` já casa com o filtro do JaCoCo, então nada muda na cobertura. O
  `MainActivity` só chama `CamerasAppContent(eglBase, rememberNavController())`.
- **`HiltTestActivity`** no source set `debug`: `@AndroidEntryPoint`, vazia, só com
  `enableEdgeToEdge()`. Ela precisa estar no manifest **do app**, e não do APK de teste, por isso
  fica em `debug` e nunca chega ao release.
- **O mesmo `configChanges` e o mesmo tema do `MainActivity`**, com um comentário nos dois manifests
  pedindo que andem juntos. Sem isso, girar a tela recriaria a activity de teste, o conteúdo do
  `setContent` sumiria, e o teste de rotação testaria outro comportamento. É o princípio da
  **fidelidade do ambiente de teste**: o host de teste deve se comportar como o de produção em tudo
  que o conteúdo percebe.
- **`CamerasAppContentTest`**: um smoke que monta a raiz na `HiltTestActivity` e espera sair do
  `Loading`. Ainda sem runner Hilt, o `EglBase` era criado no teste, e a asserção aceitava "Config
  ou Home", porque dependia das credenciais que já estavam no dispositivo.

### 12. `3a3c47f` refactor: expose the preferences serializers for isolated test stores

**Antes.** Os DataStores eram delegates `by dataStore(...)` com o serializer, o nome do arquivo e o
alias do Keystore **escritos dentro** do delegate:

```kotlin
private const val USER_PREFERENCES_KEY_ALIAS = "gdi-sys-storage"
val Context.userPreferencesDataStore by dataStore(
  fileName = "user-preferences",
  serializer = EncryptedPreferencesSerializer(UserPreferences.serializer(), UserPreferences(),
                                              KeystoreCryptoEngine(USER_PREFERENCES_KEY_ALIAS))
)
```

**Por quê.** Estado global persistente sem costura (item 4 do `e2e_testability.md`). Um delegate
`by dataStore` é **um por processo**, com arquivo fixo. Para isolar o storage por teste havia dois
caminhos: recomeçar o processo a cada teste (lento) ou montar um store de teste com o **mesmo
serializer** em outro arquivo. O `StorageModule` já fornecia os `DataStore` pelo Hilt. O que faltava
era como construí-los, e o serializer estava escondido no delegate.

Dois detalhes técnicos:

- **As factories recebem um `CryptoEngine`, não o alias.** O `KeystoreCryptoEngine` abre o
  AndroidKeyStore no construtor. Receber o engine deixa o teste escolher entre o Keystore real com
  outro alias e um engine falso no JVM (inversão de dependência).
- **As factories ficam num arquivo separado** (`PreferencesSerializers.kt`). Chamar qualquer função
  do arquivo `DataStoreKt` inicializa a classe e, com ela, o delegate e o `KeystoreCryptoEngine`. No
  JVM isso falha com `ExceptionInInitializerError`. Isso foi descoberto quando o primeiro teste
  unitário falhou: é um efeito colateral da inicialização estática das classes de arquivo do Kotlin.

**Depois.**

- `userPreferencesSerializer(crypto)` e `streamPreferencesSerializer(crypto)`, `internal`.
- Nomes de arquivo e aliases como constantes `internal`, com o aviso de que são **valores já
  publicados**: renomear qualquer um deixa sem configuração todos os usuários instalados.
- `PreferencesSerializersTest` (JVM, 3 testes): fixa os nomes e aliases publicados e faz a ida e
  volta de cada serializer com um engine reversível.
- `IsolatedPreferencesStoreTest` (instrumentado): um store pela costura, com arquivo e alias
  próprios e o Keystore real. Começa vazio, criptografa (o texto puro não aparece no arquivo) e não
  toca o arquivo de produção.

### 13. `bcb1080` build: run instrumented tests with Hilt, the orchestrator and espresso-intents

**Antes.** `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, sem Hilt de
teste e sem Orchestrator.

**Por quê.** Para trocar bindings do Hilt (§0.6), o processo de teste precisa rodar na
`HiltTestApplication`. Para zerar a permissão de câmera entre testes (necessário para o onboarding),
é preciso o Orchestrator com `clearPackageData` (§0.8). O Espresso-Intents entra para interceptar o
diálogo de consentimento de VPN.

**Depois.**

```kotlin
// HiltTestRunner.kt
class HiltTestRunner : AndroidJUnitRunner() {
  override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
    super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```

```kotlin
// app/build.gradle.kts
testInstrumentationRunner = "com.gdisys.cameras.HiltTestRunner"
testInstrumentationRunnerArguments["clearPackageData"] = "true"
testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR"; animationsDisabled = true }
sourceSets { getByName("androidTest").assets.srcDir("$rootDir/qr-code-gen/examples") }
```

- Dependências novas no `libs.versions.toml`: `runner` 1.7.0, `orchestrator` 1.6.1,
  `test-services` 1.6.0, `espresso-intents` (mesma versão do Espresso), `hilt-android-testing`
  (mesma versão do Hilt) e `kspAndroidTest(hilt.compiler)`.
- **Fixtures como fonte única:** as fixtures fictícias de QR (`qr-code-gen/examples/*.json`) viram
  assets do APK de teste, sem cópia.
- **Nível A continua verde:** os testes que não são `@HiltAndroidTest` não percebem a troca de
  `Application`.
- **Custo medido:** de 49 s para 128 s (task do Gradle). Cada teste num processo novo.
- **Desvio:** o `CamerasAppContentTest` virou `@HiltAndroidTest`, porque na `HiltTestApplication`
  uma activity `@AndroidEntryPoint` só funciona com o `HiltAndroidRule`. Ganho: o `EglBase` passou a
  ser injetado, e com o `clearPackageData` a asserção ficou exata ("Config com o `Loading`
  embaixo"), em vez de "Config ou Home".
- Achado confirmado no dispositivo: o `pm clear` revoga a câmera, mas **não** zera o app op
  `ACTIVATE_VPN`.

### 14. `64ac895` test: build the QR code route test's view model outside composition

**Antes.** O `QrCodeRouteTest` fazia `viewModel = QrCodeViewModel()` **dentro** do `setContent`.

**Por quê.** O `lintDebug` passou a acusar `ViewModelConstructorInComposable`, e com razão: um
construtor dentro de um composable roda **a cada recomposição** e cria um ViewModel novo, perdendo
o estado. Em produção quem garante a instância única é o `hiltViewModel()`. No teste, o objeto
precisa ser criado fora da composição.

**Depois.** `val viewModel = QrCodeViewModel()` antes do `setThemedContent`. O lint voltou aos 3
erros antigos, que já existiam.

### 15. `9611f66` test: isolate storage per Hilt test with a test storage module

**Antes.** Os `@HiltAndroidTest` usavam os DataStores de produção. O isolamento dependia só do
`clearPackageData`.

**Por quê.** Completa o item 4 usando a costura do `3a3c47f`. Com `@TestInstallIn`, cada teste ganha
um `SingletonComponent` novo (§0.6) e, portanto, **stores novos**, sem depender da ordem dos testes
nem do Orchestrator. É o requisito de **independência entre testes**: o resultado de um teste não
pode depender do que rodou antes.

**Depois.**

```kotlin
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [StorageModule::class])
object TestStorageModule {
  @Provides @Singleton
  fun provideTestStoreFiles(@ApplicationContext context: Context): TestStoreFiles {
    val directory = File(context.cacheDir, "test-stores/${UUID.randomUUID()}")
    ...
  }
  @Provides @Singleton @UserPreferencesStore
  fun provideUserPreferencesStore(files: TestStoreFiles): DataStore<UserPreferences> =
    DataStoreFactory.create(
      serializer = userPreferencesSerializer(KeystoreCryptoEngine(TEST_USER_PREFERENCES_KEY_ALIAS)),
      produceFile = { files.userPreferences })
  ...
}
```

- **O alias é fixo e só o arquivo muda.** Assim não se cria uma chave nova no Keystore por teste. O
  isolamento vem do arquivo, e os aliases `test-` ficam separados dos de produção.
- `TestStoreFiles` é injetável, para o teste inspecionar os arquivos.
- O `TestStorageModuleTest` prova que os repositórios de produção começam vazios, gravam nos
  arquivos do teste e nunca criam os de produção.
- O resultado são duas camadas: o Hilt isola o storage, e o Orchestrator continua zerando a
  permissão de câmera.

### Commit de documentação da fase

- `c6ea15b`: documenta a costura do storage e o runner Hilt no `architecture.md` e nos planos.

---

## 5. Fase 4: ciclo de vida e threads

Item 5 do `e2e_testability.md`: o comportamento do app estava certo, mas não era **garantido** (havia
corridas possíveis) nem **verificável**. As mudanças não alteram o que o usuário vê.

### 16. `4b14238` fix: make the startup gate decide once

**Antes.**

```kotlin
init {
  viewModelScope.launch {
    getVpnConfigStatusUseCase().collect { status ->
      if (status !is VpnCredentialsStatus.Loaded) return@collect
      if (status.hasValidCredentials) _navigateUiEvent.send(ToHome)
      else { showToast(...); _navigateUiEvent.send(ToConfig) }
    }
  }
}
```

**Por quê.** O `InitViewModel` (tela `Loading`) **fica vivo na pilha** debaixo do Config. Com um
`collect` contínuo, quando o usuário salvava um QR válido no Config, o gate reagia de novo e tentava
um `send(ToHome)` num `Channel` rendezvous **sem ninguém coletando**: a corrotina ficava suspensa ali
até o `Loading` sair da pilha. Não aparecia na tela, mas era um efeito escondido esperando para
virar bug. Um **gate decide uma vez**: a semântica correta é "pegue o primeiro estado carregado",
não "observe para sempre".

**Depois.**

```kotlin
val status = getVpnConfigStatusUseCase()
  .filterIsInstance<VpnCredentialsStatus.Loaded>()
  .firstOrNull() ?: return@launch
```

- `firstOrNull()` em vez de `first()`: se o fluxo terminasse sem `Loaded`, o `first()` lançaria uma
  exceção no `viewModelScope` e derrubaria o app. Em produção o DataStore nunca termina, mas um teste
  existente usa um fluxo finito e pegou isso.
- Teste novo, `decides once and ignores later credential changes`: o fluxo `Loading`,
  `Loaded(false)`, `Loaded(true)` gera só `ToConfig`. Ele falha no código antigo (chega um `ToHome` a
  mais) e passa no novo. É a disciplina de **ver o teste falhar antes**, para provar que ele testa
  alguma coisa.

### 17. `7f08baf` fix: apply the Home lifecycle to the tunnel through a single collector

**Antes.**

```kotlin
// HomeRoute
LifecycleEventEffect(ON_RESUME) { viewModel.connectVpn() }
LifecycleEventEffect(ON_PAUSE)  { viewModel.disconnectVpn() }

// HomeViewModel
fun connectVpn()    { viewModelScope.launch { ... connectVpnUseCase(config) ... } }
fun disconnectVpn() { viewModelScope.launch { disconnectVpnUseCase() } }
```

Cada evento de ciclo de vida disparava um `launch` próprio, e os use cases rodavam em
`Dispatchers.Default`.

**Por quê.** **Condição de corrida.** Nada coordenava as chamadas ao `GoBackend`. Num
Home → Config → Home rápido, o disconnect (do pause) podia terminar **depois** do connect (do resume
seguinte), e a Home ficava sem túnel. Dois `launch` independentes não têm ordem garantida de
término.

A solução é um padrão clássico de concorrência: **reduzir a intenção a um estado e ter um único
consumidor que a aplica em série**.

- A route só **informa** o estado (`onResumed()`/`onPaused()`).
- O ViewModel guarda a intenção num `MutableStateFlow<Boolean?>`.
- Um **único coletor** aplica `connect`/`disconnect` em sequência. As chamadas nunca se sobrepõem.
- O `StateFlow` é *conflated*: descarta estados intermediários e repetidos. Um pause e um resume que
  chegam durante um connect não geram disconnect. **O último estado sempre vence.**
- **Não** `collectLatest`, porque ele cancelaria um `setState` nativo do WireGuard no meio.
- Cada operação roda em `withContext(NonCancellable)`: uma operação que começou termina, mesmo que
  o ViewModel seja limpo. O túnel nunca fica pela metade.

**Depois.**

```kotlin
private val _tunnelWanted = MutableStateFlow<Boolean?>(null)

viewModelScope.launch {
  var tunnelRequested = false
  try {
    _tunnelWanted.filterNotNull().collect { wanted ->
      tunnelRequested = wanted
      if (wanted) connectVpn() else disconnectVpn()
    }
  } finally {
    // Limpo com um connect como última operação: o pause chegou durante ele e nunca foi aplicado.
    if (tunnelRequested) disconnectVpn()
  }
}

fun onResumed() { _tunnelWanted.value = true }
fun onPaused()  { _tunnelWanted.value = false }
```

- **Risco previsto e confirmado por teste:** a Home sai da pilha (saída do app) enquanto um connect
  roda. O `ON_PAUSE` chega, mas o coletor é cancelado no `onCleared` antes de aplicá-lo, e o túnel
  ficaria de pé. O teste falhou com `[connect]`. A correção é o `finally`: a Home é a única dona do
  túnel, então ele cai junto com ela.
- **Desvio:** o `ToConfig` de falha é enviado num `launch` à parte. O canal é rendezvous, e um
  `send` direto prenderia o coletor do túnel até a route coletar o evento.
- Testes unitários: os três de `connectVpn` viraram de `onResumed`, e entraram cinco novos (pause
  depois de resume, pause antes de qualquer resume, pause durante o connect com concorrência máxima
  1, pause e resume rápidos com um só connect, ViewModel limpo durante o connect). O
  `RecordingTunnel` usa um `CompletableDeferred` como portão para segurar o connect no meio e medir
  `maxInFlight`. É a técnica de **controlar o tempo no teste** em vez de esperar por ele.

### 18. `786fc44` refactor: move the VPN service teardown into a testable class

**Antes.** O `VpnLifecycleService` chamava `vpnRepository.disconnect()` direto, tanto no
`onTaskRemoved` quanto no `onDestroy`.

**Por quê.** Três problemas:

1. **Não era idempotente.** O `onTaskRemoved` chama `stopSelf()`, que leva ao `onDestroy`: o
   disconnect rodava duas vezes.
2. **Uma falha derrubava o app.** A exceção escapava de um `launch` num escopo sem
   `CoroutineExceptionHandler`.
3. **Lógica presa num `Service`.** Só dava para testar subindo o service real.

O padrão aplicado é o *humble object*: deixar o componente do framework como uma **casca fina** e
extrair a lógica para uma classe comum, testável no JVM.

**Depois.**

```kotlin
class VpnTeardown @Inject constructor(private val vpnRepository: VpnRepository) {
  private val mutex = Mutex()
  private var tornDown = false

  suspend operator fun invoke() = mutex.withLock {
    if (tornDown) return@withLock
    try {
      vpnRepository.disconnect()
      tornDown = true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(DEBUG_TAG, "Failed to drop the tunnel", e)
    }
  }
}
```

- **A idempotência marca o sucesso, não a chamada.** Depois de uma falha, a próxima chamada tenta de
  novo. O `Mutex` impede duas ao mesmo tempo.
- A `CancellationException` é relançada, para não quebrar o cancelamento cooperativo das
  corrotinas.
- `VpnTeardownTest` (JVM, 3 testes): o disconnect, o segundo `invoke` sem efeito, e a falha que não
  lança e é refeita.
- **Adiantando o nível B:** `FakeVpnRepository`, `FakeVpnLifecycleController` e `TestVpnModule`
  (`@TestInstallIn` no lugar do `VpnModule`). A partir daqui, **nenhum `@HiltAndroidTest` sobe o
  WireGuard real**. Os fakes são `@Singleton` com contadores atômicos (`AtomicInteger`), porque os
  use cases os chamam fora da main thread.
- `VpnLifecycleServiceTest` (instrumentado): sobe o service real com `startService`/`stopService` e
  espera o `vpnState` do fake chegar a `DISCONNECTED`, sem `sleep`. Não usa `ServiceTestRule`,
  porque o `startService` dela também faz bind, e o `onBind` devolve `null`. Foi conferido que o
  teste falha (timeout) com o `vpnTeardown()` removido do `onDestroy`.

### 19. `9875f2a` test: inject dispatchers and make instrumented tests wait for them

**Antes.** Os dispatchers eram lidos direto de `Dispatchers.IO`/`Default` em `ConnectVpnUseCase`,
`DisconnectVpnUseCase`, `VpnRepositoryImpl`, `VpnLifecycleService`, `WhepRemoteDataSourceImpl` e no
`EncryptedPreferencesSerializer`. Os testes instrumentados precisavam de `waitUntil` depois de
qualquer ação assíncrona.

**Por quê.** Pelo §0.7, o Compose Test só espera a main thread. O `waitUntil` funciona, mas é
**polling**: deixa o teste mais lento, exige escolher um timeout e esconde *o que* se está
esperando. A solução idiomática é injetar os dispatchers (dependência explícita em vez de global
estático) e, no teste, embrulhá-los num **`IdlingResource`**. O Compose Test sincroniza pelo
Espresso, então passa a esperar esse trabalho como espera a main thread.

**Depois.**

- `core/coroutines`: os qualifiers `@IoDispatcher` e `@DefaultDispatcher` e o `DispatchersModule`
  de produção. O `Dispatchers.Main` fica fixo: a main já é sincronizada, e nos unitários quem a
  troca é o `MainDispatcherRule`.
- Os serializers recebem `ioDispatcher` com padrão `Dispatchers.IO`, porque os stores de produção
  são delegates, fora do Hilt.
- O `VpnLifecycleService` cria o escopo com `by lazy`, porque o Hilt só injeta no `onCreate`,
  depois do construtor.

O núcleo da mudança:

```kotlin
class IdlingDispatcher(private val delegate: CoroutineDispatcher, private val resourceName: String)
  : CoroutineDispatcher(), IdlingResource {

  private val running = AtomicInteger()

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    running.incrementAndGet()
    try {
      delegate.dispatch(context) { try { block.run() } finally { finishOne() } }
    } catch (e: Throwable) { finishOne(); throw e }   // o delegate recusou o bloco
  }

  override fun isIdleNow() = running.get() == 0
  ...
}
```

- `TestDispatchersModule` (`@TestInstallIn`) entrega os `TestDispatchers` (globais do processo,
  porque o `IdlingRegistry` é global), e o `HiltTestRunner.onStart()` os registra.
- O `TestStorageModule` passa o IO injetado ao `scope` do DataStore e ao serializer.
- **Limite documentado:** o dispatcher conta **blocos**, não corrotinas. O que suspende fora dele
  (um `delay`, um callback nativo do WebRTC) não é esperado. Um salto entre dois dispatchers
  contados, ou de volta à main, não abre brecha, porque o próximo bloco entra na fila antes de o
  atual terminar.
- `IdlingDispatcherTest` (2 testes): o dispatcher fica ocupado enquanto o bloco roda e avisa a
  volta ao idle, e um `LaunchedEffect` vai da main ao `default`, ao `io` e volta, com
  `onNodeWithText("done")` passando **sem** `waitUntil`.
- O `CamerasAppContentTest` trocou o `waitUntil` por `runOnIdle`: "idle" agora significa
  "credenciais lidas e navegação feita".
- Unitários de `Connect/DisconnectVpnUseCase` recebem um `StandardTestDispatcher` e ganharam um teste
  cada, provando que o repositório roda no dispatcher injetado.

### Commit de documentação da fase

- `32ff7b0`: documenta a resiliência dos efeitos de ciclo de vida e as costuras de teste no
  `architecture.md` e nos planos.

---

## 6. Fase 5: Home observável

Itens 7, 8 e 9 do `e2e_testability.md`. A Home é a tela mais difícil de testar (vídeo nativo,
gestos, falhas só por toast). Esta fase a torna observável e, de quebra, melhora a UX e a
acessibilidade.

### 20. `8572f65` fix: close WHEP clients that fail or are cancelled while connecting

**Antes.**

```kotlin
val job = scope.launch {
  try {
    val whepClient = whepClientProvider.get()
    whepClient.connect(streamUrl, videoSink)
    synchronized(lock) { clients[streamUrl] = whepClient }
  } catch (e: CancellationException) { throw e }
  catch (e: Exception) { onError(e) }
}
```

**Por quê.** **Vazamento de recurso.** Um cliente que falhava ou era cancelado no meio da negociação
nunca era registrado em `clients`, então nada fechava o seu `PeerConnection` (recurso nativo). O bug
apareceu no planejamento do commit seguinte: com um botão de "tentar de novo", cada nova tentativa
de um stream com falha vazaria um `PeerConnection`. É a regra geral de **liberar um recurso em todos
os caminhos de saída**, inclusive exceção e cancelamento.

**Depois.** O cliente é obtido fora do `try`, e `whepClient.close()` é chamado tanto no `catch` de
`CancellationException` quanto no de `Exception`. O teste unitário da falha agora exige o `close`
uma vez (e não duas, depois de um `disconnect`), e o do cancelamento também.

**Complemento (`2dd8ae4`, fora da numeração).** O `WhepClientImpl.close()` chamava só
`PeerConnection.close()`, que encerra a mídia mas não libera o objeto nativo. Agora ele chama
`dispose()`, que também libera transceivers, receivers, tracks e sinks, e limpa o campo antes, para
que um segundo `close()` não faça nada. Isso vale para todas as conexões, não só as que falham. O
novo `WhepClientImplTest` (JVM, 3 testes) cobre isso. Detalhes em
`print/commit_20-8572f65-close_WHEP_clients_that_fail_or_are_cancelled_while_connecting.md`.

### 21. `09c146a` feat: show a failed stream on its card with a retry button

**Antes.** Quando um stream falhava, o `HomeViewModel` só chamava
`showToast(STREAM_CONNECTION_ERROR)`. O card ficava preto, e nada mudava no `HomeUiState`.

**Por quê.** É o item 7: **feedback só por toast, sem estado observável**. O teste de "falha de
stream" só conseguia provar que o app não tinha caído. Para o usuário, um card preto não diz nada. A
decisão (do usuário) foi um estado de erro no card, com "Tentar de novo", mantendo o toast. É o
princípio de **tornar o estado explícito**: se algo importa para o usuário, deve estar no estado de
UI, e não só num efeito colateral efêmero.

**Depois.**

- `HomeUiState.Ready.failedStreams: Set<String>`. O `HomeViewModel` adiciona a URL no `onError` e a
  remove numa nova tentativa ou num disconnect. O estado só expõe as URLs da lista atual
  (`failedStreams intersect streams.toSet()`).
- **`LocalFailedStreams` em vez de parâmetro.** Cada player é *movable content* criado uma vez por
  URL (para não recriar o `SurfaceViewRenderer` ao reorganizar o grid), e um valor capturado na
  criação nunca mudaria. Um `CompositionLocal` é lido na composição, a cada mudança. É
  `compositionLocalOf`, e não `static`, para que uma falha recomponha só os players que leem o
  valor.
- O `WebRtcVideoPlayer` desenha o `StreamErrorOverlay` por cima do vídeo, com "Câmera indisponível"
  e "Tentar de novo". O retry faz `disconnect` + `connect` **no mesmo renderer**, o mesmo caminho de
  sair e voltar.
- Testes: 4 novos no `HomeViewModelTest`, e o `WebRtcVideoPlayerTest` (instrumentado, 3 testes): o
  player real sobre uma `WebRtcConnection` que grava as chamadas, cobrindo sem overlay quando
  conectado, overlay quando falhou e o retry reconectando o **mesmo** sink (`assertSame`).

### 22. `27d87dd` test: cover the fixed grid's drag state on the JVM

**Antes.** O `PagedDragState`, que guarda toda a lógica do arraste no grid fixo, não tinha testes.

**Por quê.** Testes de gesto são os mais instáveis da pirâmide (coordenadas, velocidade, timing). A
estratégia do item 8b é **tirar do gesto o que não precisa ser gesto**: o loop de ponteiro só
alimenta chamadas ao `PagedDragState`, e essas chamadas são lógica pura, testável no JVM, rápida e
determinística.

**Depois.** `PagedDragStateTest` (JVM, 12 testes), num layout fixo (container de 400×400 com uma
página 2×2 de células 200×200). Cobre o hit test da alça, o início pelo centro da célula, a troca ao
passar por outra célula, as zonas de borda (inclusive antes de o container ser medido), o avanço de
página, a persistência só no drop (uma vez e só se a ordem mudou), o storage esperando o fim do
arraste e a célula descartada saindo do hit test. Sem mudança em produção.

### 23. `c8e3e7f` feat: expose stream playback state and accessible reordering on Home

**Antes.**

- O `onFirstFrameRendered` do renderer estava vazio. Não havia sinal de "vídeo tocando", e o card
  ficava preto até o primeiro frame.
- Reordenar só era possível por arraste: o TalkBack não reordenava, e o "Reconfigurar" do modo
  dinâmico (que aparece depois de um overscroll) também ficava fora do alcance dele.
- A Home não tinha tags por card.

**Por quê.**

- **Estado de reprodução (8a).** O nível C precisa esperar "vídeo de verdade tocando" sem comparar
  pixels nem texto. Isso é estado de View (como a resolução), não de ViewModel. O lugar certo é uma
  **propriedade semântica própria**, sem idioma.
- **Ações de acessibilidade (8b.2).** Um usuário de TalkBack não consegue arrastar. Ações
  personalizadas ("Mover para antes" / "Mover para depois") resolvem a acessibilidade e dão ao
  teste um caminho **sem coordenadas**: o mesmo `onStreamsReordered` do drop, chamado de forma
  determinística.
- **Poucos testes de gesto reais (8b.3).** Ficam só os que provam a fiação do toque: um arraste por
  modo de grid e o overscroll. O resto da lógica já está no JVM.

**Depois.**

```kotlin
enum class StreamPlaybackState { CONNECTING, PLAYING, FAILED }
val StreamPlaybackStateKey = SemanticsPropertyKey<StreamPlaybackState>("StreamPlaybackState")
var SemanticsPropertyReceiver.streamPlaybackState by StreamPlaybackStateKey
```

- O `onFirstFrameRendered` chega na thread do renderer e é **repassado à main thread** pelo
  `mainExecutor`, porque o estado do Compose pertence à thread de UI. O estado zera a cada renderer
  novo (`remember` com as mesmas chaves).
- `CONNECTING` desenha um `CircularProgressIndicator` branco no card. `FAILED` desenha o overlay do
  commit anterior.
- `CameraGridItem` ganhou `onMoveEarlier`/`onMoveLater` (`null` nas pontas da lista, então a ação nem
  aparece) e `onReconfigure` (só no modo dinâmico), expostos como `CustomAccessibilityAction`. O
  `HomeScreen` calcula a nova ordem com o `reorderedTo` já existente.
- Tags por URL: `streamCard`, `dragHandle` e `streamPlayer`. Como o card é clicável e mescla a alça,
  os arrastes acham a alça com `useUnmergedTree = true` (§0.3).
- `HomeScreenTest` (7 testes): as ações movem um lugar e somem nas pontas, o "Reconfigurar" como
  ação só no modo dinâmico, e os 3 gestos (arraste em passos com `down`/`moveBy`/`up`).
- `WebRtcVideoPlayerTest` (agora 4): o spinner em `CONNECTING`, e **frames I420 reais** injetados no
  sink numa thread à parte até um ser desenhado, levando a `PLAYING`, sem rede. O teste chama o
  `PeerConnectionFactory.initialize` ele mesmo, porque o `JavaI420Buffer` aloca pela biblioteca
  nativa, que no app quem carrega é o provider do Hilt.
- Limites conhecidos: um stream que conecta mas nunca manda frame fica no spinner, e um que cai
  depois do primeiro frame continua `PLAYING`, congelado.

### 24. `abaf348` test: run screen tests in any locale and finish the Home test tags

**Antes.**

- A rodada em pt-BR exigia `adb root` (imagem AOSP `userdebug`) e reiniciava o framework do
  emulador. Não rodava num aparelho físico.
- A Home ainda não tinha as tags de `LOADING`, grid, foco, botões de reconfigurar e de página.
- O `rememberSystemBarsController` convertia o `LocalContext` em `Activity`.

**Por quê.**

- **Locale como parâmetro do teste.** Trocar o idioma do dispositivo é estado global, lento e
  privilegiado. O teste só precisa que **a tela e a asserção usem o mesmo idioma**. Um argumento de
  instrumentação, aplicado pelo `setThemedContent` e pelo `string()`, resolve sem root. É o princípio
  de **injetar a configuração em vez de depender do ambiente**.
- **Efeito colateral em produção:** o `InTestLocale` troca o `LocalContext` por um `ContextWrapper`.
  Um `LocalContext.current as? Activity` deixaria de achar a activity. O `rememberSystemBarsController`
  passou a ler o `LocalActivity`, como o `HomeRoute` e o `ConfigRoute` já faziam: é a API certa para
  "a activity", e não depende do tipo concreto do contexto.

**Depois.**

```kotlin
val testLocale: Locale? =
  InstrumentationRegistry.getArguments().getString("testLocale")?.let(Locale::forLanguageTag)

fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) =
  setContent { InTestLocale { CamerasTheme { content() } } }

@Composable
fun InTestLocale(content: @Composable () -> Unit) {
  ...
  CompositionLocalProvider(
    LocalContext provides localized,                       // ContextWrapper com outra Resources
    LocalConfiguration provides localized.resources.configuration,
    content = content)
}
```

- Uso: `-Pandroid.testInstrumentationRunnerArguments.testLocale=pt-BR`. O `TestLocaleTest` garante
  que a tela e o `string()` concordam e seguem o argumento.
- As 7 tags novas da Home entraram em `HomeTestTags` e nos componentes (`LOADING` no `HomeRoute`,
  `STREAM_GRID` nos dois grids, `FOCUSED_STREAM`, `RECONFIGURE_BUTTON`,
  `OVERSCROLL_RECONFIGURE_BUTTON`, `PREVIOUS_PAGE_BUTTON`/`NEXT_PAGE_BUTTON`).
- `HomeScreenTest` (agora 11): o overscroll passa a usar tags e não texto, e há testes novos para o
  "Reconfigurar" sempre visível no grid fixo, os botões de página e o foco substituindo o grid.

**Bug real encontrado pela rodada em pt-BR.** No emulador, `pageButtonsMoveBetweenPages` passava em
inglês e falhava em português. Medindo os retângulos: o centro da seta "próxima página" caía dentro
do botão flutuante "Reconfigurar", porque "Reconfigurar" e "Página 1 de 2" são mais largos em
português. Em inglês, o centro escapava por pouco. **O usuário em pt-BR não conseguia trocar de
página.** É um exemplo concreto de por que rodar a suíte em mais de um idioma vale a pena:
localização muda o layout.

- **Correção:** o `PagedStreamGrid` ganhou um slot `overlay`, desenhado sobre as páginas e **acima
  dos controles de página**, como irmão do container de arraste (assim um toque nele nunca inicia um
  arraste). O `FixedStreamGrid` passa o `DraggableReconfigureButton` por esse slot.
- **Teste de regressão:** `reconfigureNeverCoversThePageButtons` exige que os retângulos não se
  sobreponham.
- Resultado: os 56 instrumentados passam em inglês e em pt-BR (cerca de 3,5 min cada). Uma
  observação prática: rodar as duas suítes ao mesmo tempo no mesmo emulador quebra as duas, porque a
  segunda reinstala os APKs no meio da primeira.

---

## 7. Fase 6: smoke com o ambiente real (nível C, fase 1)

### 25. `cc1666a` test: add a real-environment smoke for the QR to video path

**Antes.** O caminho mais importante para um release (QR → túnel WireGuard → WHEP → vídeo) só era
verificado à mão. Os testes herméticos, por definição, trocam o túnel e o WebRTC por fakes.

**Por quê.** Testes herméticos provam a lógica, mas **não provam a integração com o mundo real**:
IPv6, o endpoint de casa, o peer, o MediaMTX no Pi, as bibliotecas nativas. É o topo da pirâmide:
um teste só, caro, rodado à mão, que cobre o que nenhum outro cobre. A dificuldade é de **segurança
e de configuração**: o QR carrega uma chave privada, e o teste precisa do app de produção, não do
`HiltTestApplication` com fakes.

**Depois.**

- **Troca de runner no Gradle**, com `-PrealEnvironment`:

  ```kotlin
  if (realEnvironment) {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"   // CamerasApp + grafo real
    testInstrumentationRunnerArguments["annotation"] = "com.gdisys.cameras.e2e.RealEnvironment"
    testInstrumentationRunnerArguments["streamPath"] = realStreamPath.orEmpty()
  } else {
    testInstrumentationRunner = "com.gdisys.cameras.HiltTestRunner"
    testInstrumentationRunnerArguments["notAnnotation"] = "com.gdisys.cameras.e2e.RealEnvironment"
  }
  ```

  A escolha precisa ser feita no Gradle: o runner decide o `Application` no `newApplication`, antes
  de receber os argumentos de instrumentação. Sem a propriedade, o `notAnnotation` exclui o smoke de
  todas as rodadas herméticas. Sem as duas variáveis de ambiente, o `-PrealEnvironment` falha já na
  configuração, com uma mensagem clara (**falhar cedo**).
- **O QR nunca aparece em linha de comando nem em log.** Um argumento de instrumentação apareceria
  nas linhas de comando do Gradle e do `am instrument`. Em vez disso:
  - a task `pushRealQrPayload` faz `adb push` do `CAMERAS_E2E_QR_FILE` para
    `/data/local/tmp/cameras-e2e-qr.json`;
  - a `removeRealQrPayload` (`finalizedBy`) o apaga **mesmo com falha**;
  - o teste lê o arquivo pelo shell (`UiAutomation.executeShellCommand`, identidade `shell`), porque
    o app não lê `/data/local/tmp`, e o apaga também no `@After`;
  - numa falha de parse, o teste reporta só o tipo da exceção (mesma regra do `9200110`).
- **O caminho de produção, menos a câmera.** O JSON passa pelo `ParseUserPreferencesFromQrCodeUseCase`
  real e é gravado pelos use cases reais, nos mesmos DataStores do processo. A URL vem de
  `CAMERAS_E2E_STREAM_PATH` (ex.: `8889/cam_160`), prefixada pelo `STREAM_URL_HOST_PREFIX` do app,
  para que os colchetes do IPv6 não passem pelo shell. O consentimento de VPN é concedido com
  `appops set <pacote> ACTIVATE_VPN allow`, e o teste abre o `MainActivity` real.
- **Espera pelo sinal semântico do `c8e3e7f`:** ao menos um nó com `StreamPlaybackState.PLAYING`, em
  até 90 s.
- **Diagnóstico na falha.** Num timeout, o `whereItStopped()` diz onde o fluxo parou:
  - Config → as credenciais foram recusadas;
  - `LOADING` → o túnel não subiu;
  - `FAILED` → o WHEP falhou;
  - `CONNECTING` → conectou, mas nenhum frame chegou.

  Um teste de ambiente real que só diz "falhou" obriga a depurar do zero. Este já aponta a camada.
- O helper `exists` trata o "No compose hierarchies found" (que o Compose Test lança antes de a
  activity montar a tela) como "ainda não". Isso foi descoberto num teste do caminho de falha com
  `8889/cam_999`.
- `@RealEnvironment` e um README em `androidTest/.../e2e/` com o comando, os pré-requisitos e como
  ler cada falha.
- Fora do escopo do teste, o commit também remove o `blockReadsOutsideWorkingDirectories` do
  `.claude/settings.json`.

Validação (rodada pelo usuário): no Galaxy SM-S721B com o peer de teste e `8889/cam_10`, passou em
cerca de 4 s, sem ser pulado (`skipped="0"`). Com `8889/cam_999`, esperou 90 s e falhou com a
mensagem certa ("the tunnel is up but the WHEP negotiation failed"). A suíte hermética continuou com
56 testes, todos verdes, e o smoke excluído. **Não testado:** a rodada fora da LAN de casa (pelo
pinhole IPv6 do provedor). A **fase 2**, o mesmo teste sobre um build minificado com R8, ficou
adiada.

---

## 8. Resumo

### 8.1 Tabela dos commits de código

| # | Commit | Fase | Tipo | Em uma linha |
|---|---|---|---|---|
| 1 | `9200110` | 0 | fix | Para de logar o QR (chave privada) |
| 2 | `931e7c7` | 1 | refactor | `modifier` do `ConfigButton` vai na linha clicável |
| 3 | `9bfaff8` | 1 | feat | `stateDescription` nos passos do Config (TalkBack e testes) |
| 4 | `3d24c39` | 1 | docs (KDoc) | `enabled` do passo é só visual, de propósito |
| 5 | `f4c4349` | 1 | test | Objetos de tags e tags nas telas do nível A |
| 6 | `f4a8761` | 1 | test | `LoadingScreen` aceita `modifier` |
| 7 | `3c6ea42` | 1 | test | 30 testes de tela (Config, StreamURLs, EmptyStreams) |
| 8 | `f00aebb` | 2 | refactor | `ToastPresenter` por `CompositionLocal` |
| 9 | `0437aee` | 2 | refactor | `VpnConsent`: uma fonte só para o consentimento de VPN |
| 10 | `bd04753` | 2 | refactor | `QrCodeScanner` trocável por `CompositionLocal` |
| 11 | `4065c80` | 3 | refactor | `CamerasAppContent` + `HiltTestActivity` |
| 12 | `3a3c47f` | 3 | refactor | Serializers expostos para stores isolados |
| 13 | `bcb1080` | 3 | build | Runner Hilt, Orchestrator, Espresso-Intents |
| 14 | `64ac895` | 3 | test | ViewModel do teste fora da composição (lint) |
| 15 | `9611f66` | 3 | test | `TestStorageModule`: storage novo por teste |
| 16 | `4b14238` | 4 | fix | O gate de inicialização decide uma vez |
| 17 | `7f08baf` | 4 | fix | Coletor único aplica o ciclo de vida ao túnel |
| 18 | `786fc44` | 4 | refactor | `VpnTeardown` idempotente e testável; fakes de VPN |
| 19 | `9875f2a` | 4 | test | Dispatchers injetados e `IdlingDispatcher` |
| 20 | `8572f65` | 5 | fix | Fecha clientes WHEP que falham ou são cancelados |
| 21 | `09c146a` | 5 | feat | Card com erro e "Tentar de novo" |
| 22 | `27d87dd` | 5 | test | `PagedDragState` coberto no JVM |
| 23 | `c8e3e7f` | 5 | feat | Estado de reprodução semântico e reordenação acessível |
| 24 | `abaf348` | 5 | test | `testLocale` sem root, tags da Home, correção do botão sobre a seta |
| 25 | `cc1666a` | 6 | test | Smoke real: QR → túnel → vídeo |

### 8.2 Evolução da suíte instrumentada

| Momento | Instrumentados | Observação |
|---|---|---|
| `main` | 1 | Só o `ExampleInstrumentedTest` |
| Fim do nível A (`3c6ea42`) | 31 | 3 telas puras |
| Fim das costuras (`bd04753`) | 34 | `ToastDisplayerTest`, `QrCodeRouteTest` |
| Fim da fase 4 (`9875f2a`) | 40 | Raiz de composição, storage, service, dispatchers |
| Fim da fase 5 (`abaf348`) | 56 | Home, player, locale |
| Fim da branch (`cc1666a`) | 56 + 1 smoke | O smoke só roda com `-PrealEnvironment` |

Os unitários foram de cerca de 289 para mais de 300, com `jacocoTestCoverageVerification` verde ao
longo de toda a branch.

### 8.3 Princípios que se repetem

1. **Testabilidade e acessibilidade são a mesma coisa vista de dois lados.** `stateDescription`,
   ações personalizadas e estado de reprodução semântico servem ao TalkBack e ao teste ao mesmo
   tempo.
2. **Costuras, não gambiarras.** Cada ponto onde a UI tocava a plataforma direto ganhou uma
   interface com a implementação de produção como padrão: o app não muda, e o teste troca.
3. **Fonte única da verdade.** O consentimento de VPN em um só lugar elimina estados impossíveis,
   em produção e nos fakes.
4. **Tirar do teste lento o que pode ser rápido.** Lógica de arraste no JVM, a do service numa
   classe comum, o QR por fake. Os testes de gesto e de ambiente real ficam poucos e focados.
5. **Esperar o trabalho, não o relógio.** `IdlingDispatcher` em vez de `waitUntil`, e nunca
   `Thread.sleep`.
6. **Testar achou bugs reais.** A corrida do túnel (`7f08baf`), o túnel que ficava de pé ao sair
   durante um connect, o vazamento de `PeerConnection` (`8572f65`) e o botão que cobria a seta em
   pt-BR (`abaf348`).

### 8.4 O que falta do nível B

A infraestrutura está pronta: runner Hilt, Orchestrator, `HiltTestActivity`, `CamerasAppContent`,
`TestStorageModule`, `TestVpnModule`, `TestDispatchersModule` e as tags da Home. Ainda faltam:

- `FakeVpnPermissionRepository` (com Espresso-Intents) e `FakeStreamConnections`, que substitui o
  `StreamConnectionRepository`;
- a classe base `FlowTest`, os helpers `Seed`, `QrFixtures` e `DeviceState`, e os robots;
- os cenários #0 a #12 do §4.3 do `e2e_level_b_plan.md` (smoke do `MainActivity`, onboarding, QR,
  gate do Config, usuário recorrente, validação de URLs, orientação, ciclo de vida e falha da VPN,
  falha de stream, reconfigurar e reordenar);
- os critérios de pronto do §8 do plano, que continuam desmarcados.

O job de CI com Gradle Managed Devices e a fase 2 do nível C (build minificado) têm planos próprios
e também estão em aberto.
