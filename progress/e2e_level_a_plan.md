# Plano de ação: §6.1, nível A (telas isoladas)

Implementa o §6.1 do `e2e_research.md`: testes de Compose UI das telas puras, **sem Hilt, sem
navegação e sem fakes**. Cada teste monta um `*Screen` com um `uiState` escrito à mão e verifica
(a) o que a tela mostra para esse estado e (b) qual callback cada interação chama.

Escopo: `ConfigScreen` (com `ConfigButton`), `StreamURLsScreen` (com `AddStreamUrlForm`,
`StreamUrlRow` e `gridPreferencesSection`) e `EmptyStreamsScreen`.

Fora do escopo: `HomeScreen` / grid de vídeo (precisa de `EglBase` e WebRTC, fica para o nível B),
`QrCodeScreen` (câmera), Hilt, navegação e CI. O job de CI é o passo 3 da ordem do relatório e terá
plano próprio.

---

## 1. Pontos encontrados na leitura do código

Estes pontos mudam o que precisa ser feito antes dos testes:

| # | Achado | Onde | Consequência |
|---|---|---|---|
| 1 | **O `modifier` do `ConfigButton` é aplicado ao círculo de status, não à linha clicável.** A `Row` externa começa com `Modifier.alpha(...)` novo, e o parâmetro só aparece no `Row` interno de 30 dp | `feature/config/components/ConfigurationStepButton.kt` | Um `testTag` passado pelo `modifier` marcaria o círculo, e o `performClick` cairia fora da área clicável. É preciso mover o `modifier` para a `Row` externa (é a convenção do Compose, e todos os 8 chamadores passam `Modifier` vazio) |
| 2 | **O estado do passo não aparece na semântica.** `Done`/`Error` são só ícones com `contentDescription = ""`, e `Loading` é um spinner | idem | O teste não tem como afirmar o estado. O TalkBack também não anuncia nada. Expor um `stateDescription` resolve os dois |
| 3 | **`enabled = false` no `ConfigButton` é só visual** (`alpha 0.5`). O `clickable` continua ativo, e o clique no passo do QR sem permissão de câmera chega ao `onShowScanner`, onde o ViewModel mostra o toast `CAMERA_PERMISSION_REQUIRED_FOR_QR_CODE` | idem + `ConfigViewModel.onShowScanner` | É intencional (o toast explica o porquê), então o teste **documenta** esse comportamento: clicar no passo "desabilitado" ainda chama o callback. A semântica não marca o nó como desabilitado, e `assertIsNotEnabled()` falharia |
| 4 | **Nenhum `testTag`**, e há textos em `values` / `values-en` / `values-pt-rBR` | `res/` | Os nós são encontrados por tag. Quando o teste é *sobre* o texto (ex.: "Back to Home" vs. "Navigate to Home"), ele resolve a string com `getString`, e não com um literal |
| 5 | **`StreamURLsScreen` é uma `LazyColumn`**, então os itens fora da tela não são compostos (a seção de grid fica abaixo da dobra) | `StreamURLsScreen.kt` | Antes de interagir com a seção de grid, o teste usa `performScrollToNode(hasTestTag(...))` na lista. **Sem mudança no código do app:** basta a tag `LIST`. Confirmado no `Pixel_8a` com 6 URLs: `SAVE_GRID_BUTTON` não existe na árvore antes do scroll e existe depois. Os campos da seção podem já estar compostos (pré-carregamento da lista), então o teste rola **sempre**, sem depender do tamanho da tela |
| 6 | **Os diálogos são `AlertDialog`** (janela própria) | `StreamURLsDialogs` | O Compose Test enxerga janelas de diálogo. Os botões do diálogo precisam de tag própria porque os textos se repetem na tela: **"Cancel"** é o botão de dispensar do diálogo e também o cancelar do formulário, e **"Add URL"** é ao mesmo tempo o `contentDescription` do `+` e o texto do confirmar do formulário (o mesmo vale em pt-BR). Além disso, o rótulo do confirmar muda por diálogo ("Confirm" ou "Discard"). **Feito** no commit das tags: o `ConfirmationDialog` é compartilhado, então as duas tags cobrem os quatro diálogos. Confirmado no `Pixel_8a`: 1 janela de diálogo, 2 nós com "Cancel", 2 com "Add URL", e `DIALOG_CONFIRM_BUTTON` aciona o callback certo em cada um dos 4 diálogos |
| 7 | **`LoadingScreen` não aceita `Modifier`** | `core/components/LoadingScreen.kt` | Para afirmar o estado de carregamento, adicionar `modifier: Modifier = Modifier` (mudança compatível). **Feito:** `LoadingScreen(textLabel, modifier)`, com os três chamadores inalterados, e a tag `LOADING` aplicada no `StreamURLsScreen`. Confirmado no `Pixel_8a`: com `isLoading = true`, `LOADING` é exibido, `LIST` não existe e o `BACK_BUTTON` continua na barra superior |
| 8 | **`gridPreferencesSection` é extensão de `LazyListScope`**, não um composable solto | `GridPreferencesSection.kt` | É testada através do `StreamURLsScreen`, sem teste próprio. **É uma escolha, não uma restrição:** daria para montá-la sozinha num `LazyColumn { gridPreferencesSection(...) }` dentro do teste. Testar pela tela real é preferível porque os testes 12 a 15 cobrem também a posição da seção abaixo das URLs e a rolagem até ela (achado #5). **Sem mudança no código do app:** as tags da seção (`gridColumnsInput`, `gridRowsInput`, `gridDynamicRowsCheckbox`, `SAVE_GRID_BUTTON`) entraram no commit das tags e foram encontradas pelo `StreamURLsScreen` no `Pixel_8a` |

Nada disso exige dependência nova. `ui-test-junit4` (via BOM), `androidx-junit` e
`ui-test-manifest` (debug, que fornece a `ComponentActivity` do `createComposeRule`) já estão no
`app/build.gradle.kts`. O runner padrão também serve, porque não há Hilt neste nível.

---

## 2. Pré-requisitos

Validado em 27/10/2026 nesta máquina (Apple M1, 16 GB).

1. **Emulador local: AVD `Pixel_8a`**

   | Item | Valor | Por quê |
   |---|---|---|
   | Imagem | `system-images;android-37.0;default;arm64-v8a` (AOSP, **sem Play Store**) | A imagem com Play Store é `user` e não permite `adb root`, sem o qual o `setprop persist.sys.locale` do §5 não funciona. A `default` é `userdebug` |
   | API | 37 (Android 17) | Roda o app sem problema (`compileSdk`/`targetSdk` 36, `minSdk` 26) |
   | RAM | 4096 MB | Com 2048 MB, a API 37 ficava no limite |
   | Câmera traseira | `virtualscene` | Atende ao `android.hardware.camera` `required="true"` do manifest |

   Verificado: Compose UI Test + Espresso 3.7.0 funcionam nessa API (clique e callback num teste
   provisório com o `EmptyStreamsScreen`), e a troca para pt-BR muda as strings resolvidas
   ("Reconfigure" → "Reconfigurar").

2. **`platform-tools` (adb) 37 ou mais novo.** Com o adb `34.0.5`, o emulador da API 37 fica preso
   em `offline` (`protocol fault (couldn't read status)`). Atualizar pelo SDK Manager ou com
   `sdkmanager "platform-tools"`, e depois rodar `adb kill-server`.

3. **Cold boot.** Restaurar o snapshot `default_boot` deixou o dispositivo `offline` mesmo com o
   adb novo. Subir sem o snapshot:

   ```bash
   emulator -avd Pixel_8a -no-snapshot-load -no-snapshot-save -no-boot-anim &
   adb wait-for-device
   adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
   ```

   (No Android Studio: Device Manager → ⋮ → *Cold Boot Now*. Acrescente `-no-window` para rodar
   sem janela.)

4. **Desligar animações** no emulador (evita instabilidade em diálogos e na ripple). A configuração
   persiste no AVD, então basta uma vez:

   ```bash
   for k in window_animation_scale transition_animation_scale animator_duration_scale; do
     adb shell settings put global $k 0
   done
   ```

5. **Smoke test:** `./gradlew :app:connectedDebugAndroidTest` deve rodar o
   `ExampleInstrumentedTest` e passar no `Pixel_8a(AVD) - 17`. Se falhar aqui, o problema é de
   ambiente, não dos testes novos.

---

## 3. Mudanças no código de produção

Todas são pequenas e sem efeito visual. A única mudança perceptível é o TalkBack passar a anunciar
o estado dos passos.

### 3.1 Objetos de tags (um por feature)

Ficam junto dos componentes, como `object` com `const val`. O nome casa com o padrão
`**/*Test*.*` do `generatedCodeFilter`, então já ficam fora do JaCoCo. O Konsist usa
`scopeFromProduction()`, e esses objetos não importam nada de `.data.`.

`feature/config/components/ConfigTestTags.kt`:

```kotlin
/** Semantic tags shared by ConfigScreen and its instrumented tests. */
object ConfigTestTags {
  const val CAMERA_PERMISSION_STEP = "config_camera_permission_step"
  const val QR_CODE_STEP = "config_qr_code_step"
  const val VPN_PERMISSION_STEP = "config_vpn_permission_step"
  const val STREAM_URLS_STEP = "config_stream_urls_step"
  const val NAVIGATE_TO_HOME_BUTTON = "config_navigate_to_home_button"
}
```

`feature/streamurls/components/StreamURLsTestTags.kt`:

```kotlin
object StreamURLsTestTags {
  const val LOADING = "stream_urls_loading"
  const val LIST = "stream_urls_list"
  const val BACK_BUTTON = "stream_urls_back_button"
  const val ADD_URL_BUTTON = "stream_urls_add_url_button"
  const val NO_URLS_MESSAGE = "stream_urls_no_urls_message"
  const val ADD_URL_FORM = "stream_urls_add_url_form"
  const val PORT_INPUT = "stream_urls_port_input"
  const val STREAM_NAME_INPUT = "stream_urls_stream_name_input"
  const val CONFIRM_ADD_URL_BUTTON = "stream_urls_confirm_add_url_button"
  const val CANCEL_ADD_URL_BUTTON = "stream_urls_cancel_add_url_button"
  const val LOAD_DEFAULTS_BUTTON = "stream_urls_load_defaults_button"
  const val SAVE_URLS_BUTTON = "stream_urls_save_urls_button"
  const val SAVE_GRID_BUTTON = "stream_urls_save_grid_button"
  const val DIALOG_CONFIRM_BUTTON = "stream_urls_dialog_confirm_button"
  const val DIALOG_DISMISS_BUTTON = "stream_urls_dialog_dismiss_button"

  // Per-item tags: the URL is the LazyColumn key already, so it is unique on screen.
  fun urlRow(url: String) = "stream_urls_row:$url"
  fun removeUrlButton(url: String) = "stream_urls_remove:$url"

  fun gridColumnsInput(orientation: StreamOrientation) = "stream_urls_grid_columns:$orientation"
  fun gridRowsInput(orientation: StreamOrientation) = "stream_urls_grid_rows:$orientation"
  fun gridDynamicRowsCheckbox(orientation: StreamOrientation) = "stream_urls_grid_dynamic:$orientation"
}
```

`feature/cameras/components/HomeTestTags.kt` (por ora só o necessário):

```kotlin
object HomeTestTags {
  const val EMPTY_STREAMS_RECONFIGURE_BUTTON = "home_empty_streams_reconfigure_button"
}
```

### 3.2 `ConfigButton` (`ConfigurationStepButton.kt`)

1. Aplicar o `modifier` recebido na `Row` externa e deixar o círculo de status com `Modifier`
   próprio (achado #1).
2. Expor o estado como `stateDescription` na linha clicável, para que o nó tagueado carregue
   título, subtítulo e estado:

   ```kotlin
   val stateDescription = stringResource(uiState.stateDescriptionRes())
   Row(
     modifier = modifier
       .alpha(if (enabled) 1f else 0.5f)
       .clickable(onClick = onClicked)
       .semantics { this.stateDescription = stateDescription }
       // ...
   ```

   Não é preciso `semantics(mergeDescendants = true)`: o `clickable` já mescla os filhos. Confirmado
   no `Pixel_8a`: o nó de cada `*_STEP` é clicável, mescla os descendentes, traz título e subtítulo
   como texto e o `stateDescription` do estado (`Loading`, `Pending`, `Done`, `Error`).

3. Novas strings em `values`, `values-en` e `values-pt-rBR`:
   `config_step_state_loading`, `config_step_state_ready`, `config_step_state_done`,
   `config_step_state_error` (ex.: "Loading" / "Carregando", "Pending" / "Pendente",
   "Done" / "Concluído", "Error" / "Erro"). O mapeamento `ConfigButtonState → @StringRes` fica num
   `when` privado dentro do componente, não no `ConfigUiState`: é apresentação, e o `ConfigUiState`
   está no escopo do JaCoCo, onde um `R.string` não quer dizer nada.
4. **Não** mudar o comportamento do `enabled` (achado #3).

### 3.3 Aplicar as tags

| Arquivo | Nó | Tag |
|---|---|---|
| `ConfigScreen.kt` | Os 4 `ConfigButton` (via `modifier`) | `ConfigTestTags.*_STEP` |
| `ConfigScreen.kt` | `Button` de Home | `NAVIGATE_TO_HOME_BUTTON` |
| `StreamURLsScreen.kt` | `LoadingScreen` | `LOADING` (depende do §3.4) |
| `StreamURLsScreen.kt` | `LazyColumn` | `LIST` |
| `StreamURLsScreen.kt` | `IconButton` de voltar / de adicionar | `BACK_BUTTON` / `ADD_URL_BUTTON` |
| `StreamURLsScreen.kt` | Texto "nenhuma URL" | `NO_URLS_MESSAGE` |
| `StreamURLsScreen.kt` | `OutlinedButton` padrões / `Button` salvar URLs | `LOAD_DEFAULTS_BUTTON` / `SAVE_URLS_BUTTON` |
| `StreamURLsScreen.kt` (`ConfirmationDialog`) | `TextButton` confirmar / dispensar | `DIALOG_CONFIRM_BUTTON` / `DIALOG_DISMISS_BUTTON` |
| `StreamUrlRow.kt` | `Row` / `IconButton` de remover | `urlRow(url)` / `removeUrlButton(url)` (aplicadas pelo chamador via `modifier`, e o botão via novo parâmetro ou tag interna derivada de `url`) |
| `AddStreamUrlForm.kt` | `OutlinedCard`, os dois campos, os dois botões | `ADD_URL_FORM`, `PORT_INPUT`, `STREAM_NAME_INPUT`, `CONFIRM_…`, `CANCEL_…` |
| `GridPreferencesSection.kt` | Campos de colunas/linhas, `Checkbox`, botão salvar | `gridColumnsInput(o)`, `gridRowsInput(o)`, `gridDynamicRowsCheckbox(o)`, `SAVE_GRID_BUTTON` |
| `EmptyStreamsScreen.kt` | `Button` "Reconfigurar" | `EMPTY_STREAMS_RECONFIGURE_BUTTON` |

### 3.4 `LoadingScreen`

Adicionar `modifier: Modifier = Modifier`, aplicado à `Column`. Os chamadores atuais não mudam.

### 3.5 Verificação desta etapa

`./gradlew :app:testDebugUnitTest` (Konsist + `JacocoExclusionsTest` continuam verdes) e
`./gradlew :app:assembleDebug`. Conferir os `@Preview` no Android Studio: nada muda visualmente.

**Feito.** Todas as mudanças desta seção estão nos commits `931e7c7`, `9bfaff8`, `3d24c39`,
`f4c4349` e `f4a8761`. Os unit tests e o `assembleDebug` passaram depois de cada etapa, os previews
foram conferidos no Android Studio sem mudança visual, e o TalkBack anuncia o estado de cada passo
do Config.

---

## 4. Testes

### 4.1 Estrutura

```
app/src/androidTest/java/com/gdisys/cameras/
├── testing/
│   └── ComposeTestSupport.kt          // setThemedContent { }, string(@StringRes)
└── feature/
    ├── config/components/ConfigScreenTest.kt
    ├── streamurls/components/StreamURLsScreenTest.kt
    └── cameras/components/EmptyStreamsScreenTest.kt
```

Os pacotes espelham `main`, como o `src/test/` já faz.

`ComposeTestSupport.kt`:

```kotlin
/** Renders [content] inside the app theme, the way every screen is shown in production. */
fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) =
  setContent { CamerasTheme { content() } }

/** Resolves a string in the device locale, so assertions on text do not hardcode a language. */
fun string(@StringRes resId: Int): String =
  InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
```

Para o `StreamURLsScreen`, que tem 18 callbacks, um gravador evita repetir lambdas vazias em
todo teste:

```kotlin
/** Records every callback StreamURLsScreen fires, in order, with its arguments. */
private class StreamURLsScreenEvents {
  val calls = mutableListOf<String>()
  // onBack = { calls += "back" }, onPortChanged = { calls += "port:$it" }, ...
}

private fun ComposeContentTestRule.setStreamURLsScreen(
  uiState: StreamURLsUiState,
  events: StreamURLsScreenEvents = StreamURLsScreenEvents()
): StreamURLsScreenEvents
```

### 4.2 `ConfigScreenTest`

| # | Teste | Estado de entrada | Verifica |
|---|---|---|---|
| 1 | `eachStepExposesItsState` | Os 4 passos com estados diferentes (`Loading`, `Ready`, `Done`, `Error`) | `SemanticsMatcher.expectValue(StateDescription, string(...))` em cada tag |
| 2 | `cameraStepClickRequestsCameraPermission` | Padrão | `onRequestCameraPermission` chamado 1x, nenhum outro callback |
| 3 | `qrCodeStepClickOpensScanner` | Câmera `Done` | `onShowScanner` chamado |
| 4 | `qrCodeStepStillClickableWithoutCameraPermission` | Câmera `Ready` | `onShowScanner` **ainda** chamado (achado #3: o ViewModel decide e mostra o toast) |
| 5 | `vpnStepClickRequestsVpnPermission` | Padrão | `acceptVpnPermission` chamado |
| 6 | `streamUrlsStepClickNavigatesToStreamUrls` | Padrão | `onNavigateToStreamURLs` chamado |
| 7 | `homeButtonRequestsNavigation` | Padrão | `onNavigateToHome` chamado. O gate é do ViewModel, então a tela chama o callback mesmo com requisitos faltando |
| 8 | `homeButtonSaysNavigateWhenThereIsNoHomeBehind` | `canNavigateBackToHome = false` | Texto = `string(R.string.config_screen_navigate_to_home)` |
| 9 | `homeButtonSaysBackWhenHomeIsInTheBackStack` | `canNavigateBackToHome = true` | Texto = `string(R.string.config_screen_back_to_home)` |
| 10 | `stepsShowTitleAndSubtitle` | Padrão | O nó de cada passo (merged) contém título e subtítulo das strings |

### 4.3 `StreamURLsScreenTest`

| # | Teste | Estado de entrada | Verifica |
|---|---|---|---|
| 1 | `loadingShowsOnlyTheLoadingIndicator` | `isLoading = true` | `LOADING` exibido; `LIST` inexistente |
| 2 | `emptyListShowsNoUrlsMessage` | `streamUrls = []` | `NO_URLS_MESSAGE` exibido |
| 3 | `urlsAreListedInOrder` | 2 URLs | `urlRow(url)` de cada uma, na ordem (comparar posições `top`) |
| 4 | `removeButtonReportsItsUrl` | 2 URLs | Clique em `removeUrlButton(segunda)` → evento `remove:<segunda>` |
| 5 | `addButtonOpensForm` | `isAddFormVisible = false` | Clique em `ADD_URL_BUTTON` → `showAddUrlForm` |
| 6 | `addButtonIsDisabledWhileFormIsOpen` | `isAddFormVisible = true` | `ADD_URL_BUTTON.assertIsNotEnabled()`; `ADD_URL_FORM` exibido |
| 7 | `formFieldsReportTypedText` | Form visível, campos vazios | `performTextInput("8889")` → `port:8889`; idem nome do stream |
| 8 | `formConfirmAndCancel` | Form visível | Confirmar → `addUrlConfirmed`; cancelar → `cancelAddUrlForm` |
| 9 | `loadDefaultsAndSaveUrlsRequestConfirmation` | Padrão | Cliques → `loadDefaultsRequested`, `saveUrlsRequested` |
| 10 | `eachDialogConfirmsTheRightAction` | `dialog` = cada um dos 4 `StreamURLsDialog` (parametrizado em loop) | Título do diálogo exibido; `DIALOG_CONFIRM_BUTTON` → o callback certo (`loadDefaultsConfirmed`, `saveUrlsConfirmed`, `saveGridConfirmed`, `discardChangesConfirmed`) |
| 11 | `dialogDismissReportsDismissal` | Qualquer diálogo | `DIALOG_DISMISS_BUTTON` → `dialogDismissed` |
| 12 | `gridFieldsReportOrientation` | Grids válidos | Scroll até a seção; digitar em `gridColumnsInput(LANDSCAPE)` → `gridColumns:LANDSCAPE:<valor>`; idem linhas em `PORTRAIT` com `dynamicRows = false` |
| 13 | `rowsFieldIsDisabledInDynamicMode` | `portrait.dynamicRows = true`, `landscape.dynamicRows = false` | `gridRowsInput(PORTRAIT).assertIsNotEnabled()`, `gridRowsInput(LANDSCAPE).assertIsEnabled()` |
| 14 | `dynamicRowsCheckboxReportsToggle` | `dynamicRows = true` | Clique → `gridDynamic:PORTRAIT:false` |
| 15 | `saveGridEnabledOnlyWhenBothOrientationsAreValid` | (a) ambos válidos (`"2"`, `"1"`, dinâmico); (b) colunas `""` em retrato; (c) `"7"` (acima de `MAX_GRID_DIMENSION = 6`); (d) linhas vazias com `dynamicRows = false` | `SAVE_GRID_BUTTON` habilitado só em (a) |
| 16 | `backButtonReportsBack` | Padrão | `BACK_BUTTON` → `back` |

Os testes 12 a 15 começam com
`onNodeWithTag(LIST).performScrollToNode(hasTestTag(SAVE_GRID_BUTTON))` (achado #5).

### 4.4 `EmptyStreamsScreenTest`

| # | Teste | Verifica |
|---|---|---|
| 1 | `showsNoStreamsMessage` | Texto `string(R.string.home_screen_no_streams)` exibido |
| 2 | `reconfigureNavigatesToConfig` | Clique em `EMPTY_STREAMS_RECONFIGURE_BUTTON` → `onNavigateToConfig` chamado 1x |

### 4.5 Convenções

- Encontrar nós **por tag**. Texto só quando o teste é sobre o texto, e sempre via `string(...)`.
- Um comportamento por teste, com nome no formato `<sujeito><comportamento>`, em inglês
  (`CLAUDE.md`).
- `useUnmergedTree = true` só quando a tag estiver num filho de um nó com `mergeDescendants`
  (ex.: o botão de remover dentro de uma linha clicável, se for o caso).
- Sem `Thread.sleep`. O Compose Test sincroniza com a recomposição, e estes testes não têm I/O.

### 4.6 Resultado

**Feito.** 30 testes novos (10 + 18 + 2), mais o `ExampleInstrumentedTest`: 31 verdes no `Pixel_8a`,
3 execuções seguidas em `en-US` e 1 em `pt-BR`. `jacocoTestCoverageVerification` continua verde.

Desvios do plano:

- **Os testes 7 e 12 do §4.3 viraram dois cada** (`portFieldReportsTypedText` /
  `streamNameFieldReportsTypedText` e `gridColumnsFieldReportsOrientation` /
  `gridRowsFieldReportsOrientation`). O `uiState` do teste é fixo, então quando o foco passa para o
  segundo campo, o primeiro volta ao valor do estado e emite mais um `onValueChange` (ex.: `port:`).
  Com o ViewModel isso não acontece, porque o estado acompanha o que foi digitado. Afirmar esse
  evento extra prenderia o teste a um detalhe interno do Compose, por isso cada teste mexe num campo só.
- Os testes 12 e 15 usam `performTextReplacement`, e não `performTextInput`: os campos já têm valor,
  e o lugar onde a inserção cai depende do cursor.
- Os testes da seção de grid rolam até o **próprio nó** que vão usar (helper `onListItem`), e não
  sempre até o `SAVE_GRID_BUTTON`. Assim o teste não depende de os campos acima do botão continuarem
  compostos depois da rolagem.
- Os testes 10 e 15 passam por vários estados num único `setContent`, com um `mutableStateOf`
  (o `createComposeRule` só aceita um `setContent` por teste).
- O teste 4 do §4.2 também chama `assertIsEnabled()` no passo do QR, para documentar que a semântica
  não o marca como desabilitado (achado #3).

---

## 5. Comandos

```bash
./gradlew :app:connectedDebugAndroidTest                         # toda a suíte instrumentada
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gdisys.cameras.feature.config.components.ConfigScreenTest
# relatório: app/build/reports/androidTests/connected/debug/index.html
```

Rodar a suíte **também com o emulador em pt-BR** uma vez, para confirmar que nenhum teste depende
do idioma. Isso exige root, por isso a imagem `default` (§2):

```bash
adb root
adb shell "setprop persist.sys.locale pt-BR; stop; start"   # reinicia o framework (~15 s)
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
./gradlew :app:connectedDebugAndroidTest
adb shell "setprop persist.sys.locale en-US; stop; start"   # volta ao padrão
```

---

## 6. Documentação (exigência do `CLAUDE.md`)

| Arquivo | Mudança |
|---|---|
| `docs/architecture.md` §9.2 | Trocar "still the generated stub — open" pelo estado novo: nível A coberto e o que ainda falta (níveis B e C) |
| `docs/architecture.md` §10 | Linha nova na tabela ("Compose UI, instrumented": os três `*ScreenTest`); comando `connectedDebugAndroidTest` na lista de comandos; a convenção de `*TestTags` e de `stateDescription` no `ConfigButton` |
| `docs/architecture.md` §5.2 | Uma frase sobre `enabled` ser só visual no `ConfigButton`, e o porquê (o toast vem do ViewModel) |
| `docs/architecture.md` §10.1 | Ainda sem job instrumentado. Ajustar o texto que justifica a ausência ("still holds only the generated stub") para "o job vem no próximo passo" |
| `app/build.gradle.kts` (KDoc do `jacocoTestReport`) | Hoje diz que `src/androidTest/` "holds no test of its own yet". Passa a dizer que as telas têm testes instrumentados, mas fora do JaCoCo, que mede só unit tests |
| `e2e_research.md` | Marcar o §6.1 como feito, apontando para este plano |

---

## 7. Commits sugeridos (na `feat/e2e_tests`)

1. `refactor: apply ConfigButton's modifier to the clickable row` (achado #1, sem efeito visual).
2. `feat: expose each config step's state to accessibility` (`stateDescription` + strings nos 3
   idiomas).
3. `test: add test tags to the config, stream URLs and empty Home screens` (objetos de tags,
   aplicação e `LoadingScreen(modifier)`).
4. `test: cover ConfigScreen, StreamURLsScreen and EmptyStreamsScreen with Compose UI tests`.
5. `docs: document the screen-level instrumented tests` (architecture.md + KDoc + relatório).

O `e2e_research.md`, que está staged, pode entrar junto do commit 5 ou antes, num commit próprio.

---

## 8. Critérios de pronto

- [x] `./gradlew :app:connectedDebugAndroidTest` verde no emulador, em `en` e em `pt-BR`.
- [x] `./gradlew :app:jacocoTestCoverageVerification` continua verde (nenhum arquivo novo entra
      no escopo medido).
- [x] Rodar a suíte 3x seguidas sem falha intermitente.
- [x] Previews inalterados.
- [x] TalkBack anuncia o estado de cada passo do Config (verificação manual rápida).
- [ ] Documentação do §6 atualizada.

## 9. Riscos

| Risco | Mitigação |
|---|---|
| Diálogos ou ripple instáveis com animações ligadas | Animações desligadas no emulador (§2) |
| Teclado (IME) cobrindo o botão de confirmar o formulário | `performTextInput` não abre o IME em todos os casos; se cobrir, `performImeAction` ou `Espresso.closeSoftKeyboard()` antes do clique |
| Item da `LazyColumn` não composto | `performScrollToNode` antes de interagir (achado #5) |
| Diferença entre a API 37 (local) e a 34 (futuro CI com GMD/ATD) | Quando o GMD entrar, a suíte roda nas duas. Uma diferença de comportamento entre elas é achado, não ruído |
| Emulador `offline` depois de um boot normal | Cold boot (§2.3). Se persistir, conferir a versão do adb (§2.2) |
| Ler o texto do botão de Home sem saber em qual nó a tag está | A tag fica no `Button`, cujo texto é mesclado; usar `assertTextEquals` direto no nó |
