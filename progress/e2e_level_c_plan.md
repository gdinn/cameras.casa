# Plano de ação: §6.4, nível C (sistema e smoke real)

Base: `e2e_research.md` §6.4 e §9, item 6, e o código do `main` @ `bb63eaf` (merge do PR #21).

O smoke com VPN real já existe: `RealEnvironmentSmokeTest`, rodado à mão com `-PrealEnvironment`
(README em `app/src/androidTest/java/com/gdisys/cameras/e2e/`). Este plano cobre o que falta:

| Parte | O que | Roda onde |
|---|---|---|
| C.1 | Diálogos do sistema com UI Automator: consentimento de VPN e permissão de câmera | Emulador, junto da suíte padrão |
| C.2 | Decodificação dos PNGs de `qr-code-gen/examples/` pelo ML Kit, com os PNGs regerados | Emulador, junto da suíte padrão |
| C.3 | O smoke real contra o build minificado | Celular físico, à mão |

---

## 1. Pontos encontrados na leitura do código

| # | Ponto | Onde | Consequência |
|---|---|---|---|
| 1 | O `TestPermissionModule` troca o `PermissionModule` em **toda** a suíte, com o `FakeVpnPermissionRepository` | `androidTest/.../testing/TestPermissionModule.kt` | O teste do diálogo de VPN precisa do `VpnPermissionRepositoryImpl` real: `@UninstallModules(TestPermissionModule::class)` e um módulo aninhado com os dois bindings reais. O `@TestInstallIn` não traz o `PermissionModule` de volta, então o módulo aninhado liga as classes `Impl` |
| 2 | A câmera **já é real** na suíte: o `TestPermissionModule` liga o `CameraPermissionRepositoryImpl` | idem | Os testes da câmera são `FlowTest` comuns, sem desinstalar módulo |
| 3 | O app op `ACTIVATE_VPN` sobrevive ao `pm clear` (já registrado no `architecture.md`) | — | O teste de VPN zera o app op antes (`appops set <pacote> ACTIVATE_VPN default`) e depois, pelo shell do `UiAutomation` |
| 4 | O resultado da câmera depende do `shouldShowRequestPermissionRationale` | `ConfigRoute`, `ConfigViewModel.onCameraPermissionResult` | Negar uma vez: passo `Ready`, sem toast. Negar de novo: no Android 11+ o sistema passa a negar sozinho, o rationale fica `false` e sai o toast `CAMERA_PERMISSION_PERMANENTLY_DENIED` |
| 5 | Os toasts passam pelo `LocalToastPresenter`, e o `RecordingToastPresenter` já existe | `ToastDisplayer.kt`, `testing/RecordingToastPresenter.kt` | O `FlowTest.launchApp()` passa a fornecer um gravador (`toasts`), e os testes afirmam a mensagem. Nos fluxos do nível B, os toasts deixam de ir para a tela |
| 6 | Os PNGs são de 01/09, e os JSON mudaram em 20 e 22/10 (`pAllowedips`) | `qr-code-gen/examples/` | O teste de decodificação deve falhar com os PNGs atuais. O `main.py` ganha um modo que gera um PNG por JSON de `examples/`, com o texto do arquivo **sem reformatar** |
| 7 | O `main.py` só lê o `vpn.json`, que tem as chaves reais | `qr-code-gen/main.py` | O modo novo nunca lê o `vpn.json`, e este plano não toca nele |
| 8 | O smoke real usa classes internas do app (use cases, `DataStoreManager`) | `RealEnvironmentSmokeTest` | Num build minificado, o R8 remove ou renomeia o que o APK de teste chama. É a questão aberta da C.3 |

---

## 2. C.1: diálogos do sistema

**Dependência:** `androidx.test.uiautomator:uiautomator` 2.3.0, no `libs.versions.toml`.

**Helper `SystemDialogs`** (`testing/`), com `UiDevice`:

- `vpnConsent.accept()` / `refuse()`: espera a janela do pacote `com.android.vpndialogs` e toca
  `android:id/button1` (OK) ou `button2` (Cancel).
- `cameraPermission.allowWhileInUse()` / `deny()`: espera o botão pelo id do permission controller
  (`.*:id/permission_allow_foreground_only_button`, `.*:id/permission_deny_button`), sem fixar o
  pacote, que muda entre AOSP e Google.
- Esperas com `wait(Until.findObject(...), 10 s)`. Se o diálogo não vier, a falha diz qual.
- `resetVpnConsent()`: `appops set <pacote> ACTIVATE_VPN default`.

**Cenários** (`systemdialog/`, um por classe):

| Classe | Testes | O que afirma |
|---|---|---|
| `VpnConsentDialogTest` | `acceptingTheDialogCompletesTheStep`, `cancellingTheDialogKeepsTheStepReady` | Com o repositório real e o app op zerado: tocar o passo abre o diálogo do sistema. OK → passo `Done`, toast `VPN_PERMISSION_ACCEPTED`, e o `VpnService.prepare` passa a devolver `null`. Cancel → passo `Ready` e toast `VPN_PERMISSION_DENIED` |
| `CameraPermissionDialogTest` | `allowingCompletesTheStep`, `denyingOnceKeepsTheStepReady`, `denyingTwiceReportsPermanentDenial` | Permitir → passo `Done`. Negar uma vez → `Ready`, sem toast. Negar duas vezes → `Ready` e toast `CAMERA_PERMISSION_PERMANENTLY_DENIED` |

**Risco:** o `pm clear` pode não zerar a marca de "negado de vez" da câmera. Se o primeiro teste
sofrer com o anterior, o helper zera com `pm clear-permission-flags`.

---

## 3. C.2: decodificação dos PNGs

- `main.py --examples`: para cada `examples/*.json`, gera o `.png` ao lado com o texto do arquivo
  como está. O modo padrão (`vpn.json`) não muda.
- `QrFixture` ganha `readImage()` (o `.png` com o mesmo nome).
- `QrCodeImageDecodingTest` (`feature/qrcode/`): para cada fixture, decodifica o PNG com
  `BarcodeScanning` e `InputImage.fromBitmap`, e compara o texto lido com o JSON. O modelo do ML Kit
  vem empacotado no app, então funciona sem rede.
- Se uma comparação falhar, a mensagem diz só o nome da fixture, sem imprimir o JSON (regra do
  §4.4 do relatório: as fixtures são fictícias, mas o hábito vale).

---

## 4. C.3: smoke no build minificado (em aberto)

O relatório sugere um build type derivado do release, assinado com a chave de debug. O problema é
o ponto #8: o smoke chama classes internas, e o R8 não sabe disso. As saídas:

| Opção | Como | Custo |
|---|---|---|
| A | Build type `e2eRelease` minificado, com regras `-keep` só para o que o smoke chama, num arquivo `proguard-e2e.pro` que só esse build type usa | Médio. As regras extras mudam o build em relação ao release, mas pouco |
| B | O smoke passa a dirigir o app só pela tela, sem chamar classes internas. O QR entra por um caminho de depuração do build `e2eRelease` | Alto, e acrescenta código de teste ao app |
| C | Gerar o APK de release e rodar o roteiro manual (QR → túnel → vídeo) a cada release, como hoje | Zero, mas continua manual |

**Recomendação:** a opção A, decidida antes de começar a C.3. Ela só roda no celular de quem tem o
QR.

---

## 5. Documentação

- `docs/architecture.md` §9.2: as três linhas desatualizadas (os fluxos agora cobrem orientação,
  reconfigurar e reordenar; os diálogos ficam cobertos).
- §10: as novas classes na tabela, o helper `SystemDialogs`, o gravador de toasts do `FlowTest`, e
  o modo `--examples` do `main.py`.

## 6. Critérios de pronto

- [ ] C.1 e C.2 verdes no `Pixel_8a`, em `en` e em `pt-BR`.
- [ ] Os diálogos passam 3 vezes seguidas.
- [x] O teste de decodificação falha com os PNGs antigos e passa com os regerados.
- [ ] A C.3 decidida e, se for a opção A, rodada no celular.
- [ ] Documentação atualizada.

---

## 7. Resultados

**C.1 e C.2 — feitas.** Na segunda execução das 3 classes, os 6 testes passaram. Faltam os critérios do §6 (suíte inteira em `en` e `pt-BR`, e os diálogos 3 vezes seguidas).

- C.2: `main.py --examples` (o modo padrão virou `generate_from_vpn_json()`, sem mudar o que faz),
  `QrFixture.readImage()` e `QrCodeImageDecodingTest`, um teste só que lista as fixtures que não
  batem. Os PNGs ainda **não** foram regerados: a primeira execução deve falhar, para provar que o
  teste pega PNG desatualizado.
- C.1: `SystemDialogs` (`VpnConsent.accept/cancel/reset`, `Permission.allowWhileInUse/deny`),
  `VpnConsentDialogTest` (2) e `CameraPermissionDialogTest` (3), no pacote `systemdialog/`.
- **Desvio (ponto #1):** a primeira compilação falhou: o Hilt não aceita `@UninstallModules` de um
  módulo `@TestInstallIn`. O `FakeVpnPermissionRepository` ganhou `useSystemConsent`, que repassa a
  pergunta ao `VpnPermissionRepositoryImpl` real, e o teste liga esse modo antes do `launchApp()`.
- **Desvio:** o `FlowTest.launchApp()` passou a gravar os toasts (`toasts.shown`) em todos os
  fluxos, e não só nos do nível C. Nos fluxos do nível B, os toasts deixam de aparecer na tela.
- Documentação: o §9.2 do `architecture.md` foi reescrito (as três linhas desatualizadas saíram, e
  o que segue aberto é o feed da câmera do scanner e o build minificado). O §8.3 ganhou o modo
  `--examples` e o nome certo dos PNGs (`qrcode-<endereço>.png`). Contagem: 110 instrumentados.

**Primeira execução das 3 classes (C.1 e C.2):**

- C.2: o `QrCodeImageDecodingTest` falhou nas 4 fixtures, como previsto: os PNGs antigos não
  decodificam para os JSON atuais. Os PNGs foram regerados com `main.py --examples`.
- `VpnConsentDialogTest` (os 2): o diálogo, o passo e o `VpnService.prepare` se comportaram como
  esperado. A falha foi a lista de toasts: a primeira execução também mostra
  `init_screen_no_credentials`. Os testes passaram a afirmar só o último toast.
- `CameraPermissionDialogTest`: permitir e negar uma vez passaram. No segundo pedido, o botão
  "Don't allow" não foi achado pelo id `permission_deny_button`: o sistema usa outro id quando a
  negação passa a ser definitiva. O seletor aceita também `permission_deny_and_dont_ask_again_button`,
  e uma falha futura lista os botões clicáveis na tela.

**Segunda execução das 3 classes:** os 6 testes passaram. O id do segundo "Don't allow" era mesmo
`permission_deny_and_dont_ask_again_button`, e o `pm clear` entre testes zerou a negação definitiva
da câmera: os três testes passaram em sequência, sem `clear-permission-flags`.
