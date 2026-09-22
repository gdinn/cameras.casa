---
layout: default
title: "Política de Privacidade — Cameras App"
description: "Política de privacidade do Cameras App (com.gdisys.cameras): o que fica armazenado no seu dispositivo, o que nunca é coletado e por que cada permissão é necessária."
lang: pt-BR
permalink: /privacy-policy/pt-br/
alt_href: "../en-us/"
alt_lang: en-us
alt_label: "English"
home_href: "../../"
---

# Política de Privacidade — Cameras App

**ID da aplicação:** `com.gdisys.cameras`  
**Última atualização:** 22 de setembro de 2026

## Resumo

O Cameras App é um cliente Android nativo para assistir a câmeras IP que ficam na **sua
própria** rede privada. Ele se conecta a essa rede por meio de um **túnel VPN WireGuard que
você fornece** e reproduz o vídeo via WebRTC.

**O desenvolvedor não opera nenhum servidor utilizado por este aplicativo.** Não existe conta,
não existe cadastro, não existe serviço em nuvem intermediando nada e não existe analytics.
Todos os dados manipulados pelo aplicativo permanecem no seu dispositivo ou trafegam
diretamente entre o seu dispositivo e a sua própria rede.

## 1. Responsável

Esta política se aplica ao aplicativo Android "Cameras", distribuído sob o ID de aplicação
`com.gdisys.cameras`.

Contato para questões de privacidade: `contato@igordebastiani.com`

## 2. Dados armazenados no seu dispositivo

O aplicativo armazena apenas o necessário para reconectar à sua própria rede e exibir suas
câmeras conforme você configurou:

| Dado | Origem | Finalidade |
|---|---|---|
| Credenciais WireGuard — chave privada do dispositivo, endereço do túnel, chave pré-compartilhada | Lidas do QR code que você gera | Estabelecer o túnel VPN até a sua rede |
| Parâmetros de rede do WireGuard — chave pública do servidor, endpoint do servidor (`host:porta`), faixas de IP roteadas, DNS do túnel, MTU, intervalo de keepalive | Lidos do mesmo QR code | Configurar o túnel |
| URLs dos streams das câmeras | Digitadas ou editadas por você no aplicativo | Saber quais streams reproduzir |
| Preferências de exibição — ordem das câmeras e geometria da grade (colunas, linhas, alternância de linhas dinâmicas), armazenadas separadamente para retrato e paisagem | Definidas por você no aplicativo | Montar a grade de câmeras |

**Esses dados nunca saem do seu dispositivo.** Não são enviados ao desenvolvedor nem a
qualquer terceiro.

### Como são armazenados

Tudo o que está listado acima é persistido em arquivos locais do Jetpack DataStore
**criptografados com AES, usando uma chave mantida no Android Keystore**. A chave é vinculada
ao dispositivo e não pode ser extraída dele.

Esses arquivos são **excluídos do backup em nuvem do Android e da transferência entre
dispositivos** (`backup_rules.xml` e `data_extraction_rules.xml`). Portanto, sua chave privada
WireGuard nunca vai para os servidores de backup do Google nem para um aparelho novo — ao
trocar de dispositivo, você escaneia o QR code novamente.

## 3. Dados que o aplicativo NÃO coleta

O aplicativo **não** coleta, armazena ou transmite:

- Identificadores pessoais — nenhum nome, e-mail, telefone ou conta de qualquer tipo.
- Identificadores de dispositivo ou de publicidade.
- Dados de localização.
- Analytics de uso, telemetria, relatórios de falha ou métricas de desempenho.
- Contatos, arquivos, fotos, áudio do microfone ou qualquer outro conteúdo do seu dispositivo.

O aplicativo **não contém SDK de analytics, SDK de publicidade nem SDK de relatório de falhas**.

## 4. Permissões e por que são necessárias

### Câmera (`android.permission.CAMERA`)

Usada **exclusivamente para ler o QR code de provisionamento** que carrega sua configuração
WireGuard. A leitura é feita inteiramente **no dispositivo** pelo leitor de códigos de barras
do Google ML Kit, que é embarcado no aplicativo e não requer conexão de rede.

Os quadros da pré-visualização da câmera são analisados em memória e descartados. **Nenhuma
imagem, foto ou vídeo é salvo em disco ou transmitido para lugar algum.** A câmera fica ativa
apenas enquanto a tela de leitura do QR code está aberta.

### Internet (`android.permission.INTERNET`)

Usada para duas coisas, ambas apontando para infraestrutura que **você** controla:

1. Estabelecer o túnel WireGuard com o endpoint do servidor VPN definido no seu QR code.
2. Abrir uma sessão WHEP/WebRTC com o seu próprio servidor de mídia (por exemplo, um Raspberry
   Pi na sua rede) para receber o vídeo das câmeras.

### VPN (serviço de VPN do Android)

O aplicativo usa o serviço de VPN do Android para levantar o túnel WireGuard. Na primeira vez,
o próprio sistema Android pedirá seu consentimento explícito por meio de uma caixa de diálogo.

**O túnel só fica ativo em primeiro plano.** Ele é levantado quando a tela das câmeras retoma e
derrubado quando ela é pausada, com um serviço de ciclo de vida como rede de segurança caso a
tarefa do aplicativo seja removida. O tráfego do seu dispositivo não é roteado pela rede das
câmeras quando o aplicativo não está em uso.

## 5. Streams de vídeo e tráfego de rede

O vídeo das câmeras trafega **diretamente** entre o seu dispositivo e o servidor de mídia da
sua própria rede, dentro do túnel WireGuard criptografado. Em relação a terceiros, é ponto a
ponto: **nenhum relay, nenhuma nuvem e nenhum servidor operado pelo desenvolvedor participa do
caminho do vídeo.**

O desenvolvedor não tem qualquer capacidade de ver, gravar ou acessar seus streams.

Como tanto o servidor VPN quanto o servidor de mídia são operados por você, qualquer registro
(log) que eles façam é regido pela sua própria configuração, e não por este aplicativo.

## 6. Componentes de terceiros

O aplicativo embarca as bibliotecas abaixo. Nenhuma delas é usada para coletar dados sobre você:

- **Google ML Kit Barcode Scanning** — decodificação do QR code, embarcada e executada no
  dispositivo.
- **WireGuard para Android (`com.wireguard.android:tunnel`)** — implementação do túnel VPN.
- **Stream WebRTC Android** — reprodução de vídeo via WebRTC.
- **AndroidX / Jetpack (Compose, CameraX, DataStore, Navigation, Lifecycle)** — framework da
  aplicação.

O aplicativo não integra nenhuma rede de publicidade, SDK de atribuição ou login social.

## 7. Retenção e exclusão de dados

Como todos os dados são locais, o controle é integralmente seu:

- **Redefinir a configuração de streams** pela tela de URLs de stream do aplicativo.
- **Limpar os dados do aplicativo** em Configurações do Android → Apps → Cameras →
  Armazenamento → Limpar dados.
- **Desinstalar o aplicativo** — isso remove permanentemente os arquivos criptografados do
  DataStore e a chave associada no Android Keystore.

Não existe cópia no servidor para o desenvolvedor apagar, nem solicitação de exclusão a ser
aberta.

## 8. Privacidade de crianças

O aplicativo não é direcionado a crianças e não coleta conscientemente dados de ninguém,
incluindo crianças menores de 13 anos.

## 9. Segurança

- A configuração persistida é criptografada em repouso (AES, chave no Android Keystore).
- Todo o tráfego remoto ocorre dentro de um túnel WireGuard (criptografia ChaCha20-Poly1305).
- HTTP em texto claro é permitido para exatamente um host — o servidor de mídia alcançável
  somente dentro do túnel — e é bloqueado em qualquer outro caso pela configuração de segurança
  de rede do aplicativo.
- O QR code de provisionamento contém material criptográfico secreto. Trate-o como uma senha:
  não o compartilhe e gere um distinto para cada dispositivo.

Nenhuma medida de segurança é absoluta, mas o aplicativo foi desenhado de modo que um
comprometimento do desenvolvedor ou de qualquer terceiro não possa expor seus dados, porque
nenhum dos dois chega a possuí-los.

## 10. Alterações nesta política

Qualquer alteração nesta política será publicada no repositório do aplicativo, com a data de
"Última atualização" acima revisada. Mudanças materiais também serão refletidas na ficha do
Google Play.

## 11. Contato

Dúvidas sobre esta política: `contato@igordebastiani.com`
