# Host Android e baseline de plataforma

> Evidência de I1 / [#7](https://github.com/RogerVFbr/memora/issues/7), coletada em 2026-10-25.

Consulte o [recorte aprovado](plans/01-primeira-versao-instalavel.md#2-recorte-aprovado)
antes de ampliar este host. O [PRD §6](00-PRD.md#6-arquitetura-lógica-proposta)
define a arquitetura do produto; aqui ficam as escolhas concretas deste incremento.
Comandos de operação têm fonte única no [README](../README.md#host-android-local).

## 1. Host e fronteira

Projeto Gradle independente em `apps/android`, com uma Activity Kotlin e sem
Compose ou serviço de fundo. O canal nativo específico está descrito no
[contrato da TV](#5-controle-persistência-e-lifecycle-da-tv). `br.com.memora` é a identidade
estável reservada na configuração; debug acrescenta `.dev` e rótulo Memora Dev,
evitando conflito de assinatura com a futura release. A assinatura persistente
e a fonte consolidada de metadados de versão serão tratadas em I4; este build
usa assinatura debug e não é uma release distribuível.

A I2 concretiza `apps/tv` e `packages/presentation`: o primeiro contém somente a
entrada de TV; o segundo contém apresentação, controlador de estado, modelos,
ícones, estilos e mídia. O catálogo demonstrativo é injetado pelo contrato
`Catalog`, não importado pela apresentação. `apps/prototype` conserva a bancada
e os simuladores, consumindo a mesma apresentação. A proveniência das fotografias
permanece no [documento visual](01-identidade-visual-e-ux.md#7-mídia-e-proveniência).

Os três pacotes são workspaces npm, com dependências e lockfile na raiz. O Gradle
registra uma tarefa geradora de assets por variante, dependente das fontes e da
configuração/lockfile, e injeta o `versionName` efetivo no build Vite. A API de
fontes geradas do AGP determina o diretório final; a linha de execução resolve
esse caminho no início da tarefa. Não há cópia manual nem dependência de Vite
em execução. Ambos os frontends usam `base: './'` e target `chrome150`, escolhido
a partir da consulta de WebView abaixo; compatibilidade no aparelho continua
pendente de observação. O CSS nativo foi preservado, sem Tailwind ou polyfills
adicionais. A entrada de TV ocupa o viewport, inclui o seletor de tema em Ajustes
e inicia pausada com controles visíveis. A UI mostra a versão injetada no APK.


`WebViewAssetLoader` serve exclusivamente `/assets/` sob
`https://appassets.androidplatform.net`. A política rejeita outra origem,
HTTP, portas explícitas, credenciais, caminhos fora dos assets e escapes de
caminho. Cada requisição é interceptada: assets ausentes e requisições recusadas
recebem resposta local de bloqueio, sem fallback de rede. Acesso a arquivos e
content providers, mixed content, janelas adicionais e rede estão desabilitados;
o manifesto não pede INTERNET. A I2 habilita JavaScript e DOM storage para React
e preferências locais. A CSP permite somente scripts e recursos locais, com estilo
inline necessário às propriedades CSS dinâmicas do React; não permite eval,
conexões, frames ou formulários. O único canal JS → nativo é a solicitação de tela ligada, restrita à origem
local e ao frame principal, conforme o contrato abaixo.

A Activity encaminha pausa/retomada ao WebView e o destrói ao encerrar. A fonte de playback da demonstração é o reducer React compartilhado; o avanço
é suspenso sob menus e com documento/Activity sem visibilidade. Consulte o
[contrato da TV](#5-controle-persistência-e-lifecycle-da-tv) para eventos e tela
ligada. `WebView.onPause()` sozinho não comprova suspensão dos timers JavaScript.

O manifesto declara launcher Android e Leanback, ausência de exigência de toque,
orientação landscape, banner e ícone locais, backup desabilitado e Activity de
entrada exportada. Launcher, área útil/tela inteira, controle físico e efeitos
visuais ainda exigem teste real; não foram instalados nem iniciados APKs nesta etapa.

## 2. Toolchain

| Componente | Versão escolhida | Motivo |
|---|---|---|
| JDK | 17; execução local Temurin 17.0.21+9 | Linha exigida pelo AGP escolhido; bytecode Java/Kotlin 17 |
| Gradle | 8.11.1, Wrapper com SHA-256 | Versão compatível com AGP 8.9; distribuição verificável |
| Android Gradle Plugin | 8.9.2 | Patch estável da linha compatível com API 35 |
| Kotlin Android | 2.1.20 | Compilador fixado, sem necessidade de Compose |
| Android SDK | compile/target 35; min 30 | Compila com API 35 e restringe o baseline a Android 11+ |
| Build Tools | 35.0.0 | Versão compatível explicitamente fixada |
| Command-line Tools | 19.0, pacote 13114758 | Instalação local do SDK sem IDE |
| AndroidX WebKit | 1.12.1 | AssetLoader sem bridge própria nem servidor |

As dependências resolvidas do build debug são fixadas pelo lockfile Gradle. As ferramentas
baixadas para esta máquina ficam em `.local/toolchain`, fora do código versionado;
`JAVA_HOME` e `ANDROID_HOME` são configurações locais, não caminhos fixos do build.
Sem NDK, emulador, CI remoto ou dependências de reprodução antecipadas.

Fontes consultadas: [compatibilidade AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes),
[conteúdo local WebView](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)
e [Gradle 8.11.1](https://docs.gradle.org/8.11.1/release-notes.html).

## 3. Baseline em leitura

A skill `firestick-check` confirmou conexão ADB autorizada e identidade esperada
Amazon / AFTKRT / karat. Resultado **parcial**: o dispositivo estava em repouso,
e a consulta não retornou Activity em primeiro plano. Nenhuma ação foi executada
para despertar, abrir aplicativo ou alterar o aparelho. O diagnóstico bruto
permanece em arquivo temporário privado fora do Git.

| Consulta observada | Resultado sanitizado |
|---|---|
| Android | 11 |
| WebView ativo | `com.amazon.webview.chromium`, `150.amazon-webview-v150-7871-tv.7871.181.20` |
| Tamanho físico reportado por `wm size` | 1920 × 1080 |

Esses dados identificam a plataforma consultada; não comprovam resolução do DOM,
saída HDMI 4K, suporte a efeitos CSS, FPS ou consumo de memória da aplicação.
O pacote WebView pode ser atualizado pelo sistema: repetir a consulta na validação
real. A ausência de foreground não impede o build I1, mas essa medição precisa
ser repetida com Activity visível, após a autorização específica para iniciá-la,
antes de aceitar comportamento visual/lifecycle em I3. Não inferir foreground,
renderização ou compatibilidade a partir do desktop. SPK-06 continua aberto.

## 4. Verificações

Build debug, dois testes da política de navegação e lint foram aprovados.
A inspeção com `aapt dump badging` confirmou pacote de desenvolvimento, SDKs,
launcher e Leanback; `unzip -l` confirmou HTML/CSS no APK; `apksigner verify
--verbose` confirmou assinatura debug válida (v2). Resultados detalhados e
comandos executados ficam na issue e no PR de I1. Validação em hardware limita-se às
consultas acima: não há aceite de instalação, launcher, D-pad ou lifecycle.

A I2 foi verificada também com `npm ci`, builds dos dois frontends e smoke
Playwright do laboratório e dos arquivos estáticos extraídos do APK. Foram
conferidos os três layouts, mídia carregada e geometria dos slots, preservar/
preencher, temas, ajustes, persistência, vazio/recuperação e retorno de foco.
A entrada TV não contém os CTAs dos simuladores; seus recursos não fizeram
requisições externas no smoke. A inspeção do APK confirmou oito assets e versão
injetada. Evidências detalhadas e limitações ficam na issue #8 e no PR respectivo.

## 5. Controle, persistência e lifecycle da TV

Implementação I3 preparada; **aceite no aparelho ainda pendente** na
[issue #9](https://github.com/RogerVFbr/memora/issues/9). Os comportamentos abaixo
são o contrato implementado e testado localmente, não observações de hardware.

### Entrada e foco

D-pad e OK seguem o encaminhamento padrão do WebView para setas e ativação do
elemento em foco. Menu, Back e teclas de mídia recebidas pela Activity são
convertidas em comandos fixos `menu`, `back`, `toggle`, `play` e `pause`, uma vez
por pressionamento; não se repetem durante key repeat. A disponibilidade dessas
teclas no controle Fire TV precisa ser confirmada no aparelho. Voltar fecha o
painel, depois os controles; na cena limpa abre os controles, preservando a
navegação de referência do laboratório.

Na entrada TV, seletores usam botões locais: esquerda/direita percorre opções,
OK escolhe a próxima e cima/baixo navega entre linhas. A bancada mantém selects
nativos. Abertura foca play/pausa; fechamento do painel restaura seu acionador,
inclusive após remontagem. Comandos de mídia fecham painéis/controles e mantêm
o viewport em foco. A Activity solicita ocultar barras do sistema, sem alegar
que a área de renderização já foi medida no Fire TV.

### Contrato nativo mínimo

O host usa `WebViewCompat.addWebMessageListener`, condicionado ao suporte a
`WEB_MESSAGE_LISTENER`, com allowlist exata da origem HTTPS embarcada e aceitação
somente do frame principal. A API `MemoraHost.postMessage` reconhece apenas
`awake:1` e `awake:0`. Não permite URLs, arquivos, tokens ou execução de código;
não usa `addJavascriptInterface`. Se o recurso não existir, o host informa que
o componente do sistema precisa ser atualizado e não carrega a apresentação.

Nativo → JS envia dois eventos fixos: `memora:visibility` com booleano e
`memora:command` com um dos comandos acima. A visibilidade exige Activity
retomada e janela em foco; também é combinada com `document.hidden` no frontend.
O reducer limpa a intenção de reprodução, fecha painel e mostra controles ao
suspender/retomar. Notificações duplicadas não reiniciam playback. O timer de
cena é cancelado quando suspenso, pausado ou com menu/painel aberto; voltar à
reprodução cria um intervalo completo, sem recuperar tempo invisível.

A UI solicita manter a tela ligada em reprodução efetiva ou com menu/painel
visível. Pausa explícita com controles fechados e background liberam a
solicitação. A camada nativa aplica `FLAG_KEEP_SCREEN_ON` somente se a Activity
estiver retomada e com foco, independentemente de mensagens atrasadas. Não há
wake lock, serviço de fundo, detecção de presença ou garantia sobre HDMI/TV
fisicamente desligada. A verificação da flag real continua pendente no hardware.

### Preferências e evolução

A persistência é `localStorage` da origem HTTPS estável do aplicativo. Guarda
somente o envelope de versão 1 e os ajustes conhecidos, sob chave própria da
entrada TV; não guarda posição temporal, estado de play, credenciais ou mídia.
O pacote de aplicação e a origem devem permanecer estáveis no upgrade para
preservar os dados. A comprovação de upgrade pertence à I6.

Mantém-se o esquema 1 já introduzido, sem migração artificial: campos ausentes
recebem defaults, valores inválidos são normalizados e coleções desconhecidas
são filtradas pelo catálogo. JSON inválido ou esquema antigo sem contrato
suportado retorna aos defaults. Um envelope numérico de versão superior a 1
não é sobrescrito: a UI usa defaults em memória e informa falha de gravação.
Falhas de armazenamento não impedem a apresentação. Uma evolução de esquema
exigirá migrador explícito e teste de compatibilidade antes de publicar.

### Evidências e pendências

Sete testes locais cobrem retomada sem autoplay, suspensão/menu/pausa, notificações
duplicadas e comandos desconhecidos, round trip de ajustes não padrão, dados
inválidos, esquema futuro e falha de armazenamento. O smoke browser exercitou
D-pad nos ajustes, foco, avanço cronometrado, suspensão maior que um intervalo,
retorno pausado, comandos e persistência. Mensagens de tela ligada foram
conferidas com host simulado; isso não valida a flag Android.

Consulta I3 pela skill `firestick-check` confirmou acesso em leitura, mantendo a
limitação de foreground do [baseline](#3-baseline-em-leitura). A consulta de
pacotes não encontrou instalação Memora. Nenhuma instalação, inicialização,
key event injetado, suspensão forçada ou alteração de configuração foi executada.

Para aceitar I3, falta instalar o APK de desenvolvimento mediante autorização
específica e depois obter as autorizações de cada operação necessária no aparelho:
abrir o app, verificar controle físico/visibilidade/retomada, preferências não
padrão, DOM/resolução, erros e flag de tela ligada. Registrar evidências sanitizadas
na issue e atualizar a central antes de encerrar. I4 permanece dependente desse
aceite; não usar o resultado do desktop como substituto.

Fontes consultadas: [WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat)
e [tela ligada](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).
