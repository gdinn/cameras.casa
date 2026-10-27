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

## 2. Mesma informação lida em vários lugares

O consentimento de VPN é lido em três pontos:

- o `VpnPermissionRepository` (estado inicial do passo);
- o `VpnRepository.getVpnPermissionIntent()` (decisão no clique);
- o `prepare` do route.

Fakes independentes geram combinações impossíveis em produção. Por isso o plano do nível B usa as
permissões reais e controla o estado do sistema.

## 3. Composition root fechado no `MainActivity`

O `NavController` é criado dentro do `setContent` e não fica acessível. O teste não consegue gravar
o QR no `savedStateHandle` nem ler a pilha. Por isso o plano do nível B extrai o `CamerasAppContent`
e cria a `HiltTestActivity`.

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

- o route pedir o `Intent` a uma interface;
- um `QrCodeSource` injetável;
- um `ToastPresenter`;
- a raiz de composição separada da activity.

O resto (lifecycle, threads, nativo, VPN real) é inerente ao que o app faz e se resolve com a
estratégia de teste, não com refatoração.
