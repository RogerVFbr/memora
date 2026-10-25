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
evitando conflito de assinatura com a release. A variante debug usa assinatura
de desenvolvimento; a configuração de versão e a assinatura permanente de
release estão na [entrega Android](03-entrega-android.md#1-configuração-e-fronteiras).

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
a partir da consulta de WebView abaixo; os comportamentos observados no aparelho
estão nas [evidências de I3](#5-controle-persistência-e-lifecycle-da-tv). O CSS nativo foi preservado, sem Tailwind ou polyfills
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
entrada exportada. A área de renderização e a operação do host foram observadas
em I3; o aceite posterior da identidade de release pelo launcher está na
[central do plano](plans/01-primeira-versao-instalavel.md#5-central-de-progresso).

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

Na consulta inicial de I1, a skill `firestick-check` confirmou conexão ADB
autorizada e identidade esperada
Amazon / AFTKRT / karat. Resultado **parcial**: o dispositivo estava em repouso,
e a consulta não retornou Activity em primeiro plano. Nenhuma ação foi executada
para despertar, abrir aplicativo ou alterar o aparelho. O diagnóstico bruto
permanece em arquivo temporário privado fora do Git.

| Consulta observada | Resultado sanitizado |
|---|---|
| Android | 11 |
| WebView ativo | `com.amazon.webview.chromium`, `150.amazon-webview-v150-7871-tv.7871.181.20` |
| Tamanho físico reportado por `wm size` | 1920 × 1080 |
| Janela Memora visível, observada em I3 | Frame nativo 1920 × 1080 |
| Viewport WebView, observado em I3 via DevTools em leitura | 960 × 540 CSS; devicePixelRatio 2 |
| Container queries CSS, observado em I3 | `CSS.supports('container-type', 'inline-size')` verdadeiro |

Os dados caracterizam a plataforma e a renderização observadas; não comprovam
saída HDMI 4K, compatibilidade de todo efeito CSS, FPS ou consumo combinado de
memória nativa/renderer.
O pacote WebView pode ser atualizado pelo sistema: repetir a consulta na validação
real. A ausência de foreground da consulta inicial I1 foi superada em I3 após
a abertura autorizada e o despertar manual pelo desenvolvedor: o diagnóstico
passou a completo, com Activity Memora retomada e janela visível. Os valores
do viewport acima são leituras do WebView real. Eles não demonstram saída 4K
ou estabilidade prolongada; SPK-06 continua aberto.

## 4. Verificações

Build debug, dois testes da política de navegação e lint foram aprovados.
A inspeção com `aapt dump badging` confirmou pacote de desenvolvimento, SDKs,
launcher e Leanback; `unzip -l` confirmou HTML/CSS no APK; `apksigner verify
--verbose` confirmou assinatura debug válida (v2). Resultados detalhados e
comandos executados ficam na issue e no PR de I1. Naquela entrega, a validação
em hardware limitou-se às consultas em leitura; as evidências operacionais
posteriores estão na seção de I3 abaixo.

A I2 foi verificada também com `npm ci`, builds dos dois frontends e smoke
Playwright do laboratório e dos arquivos estáticos extraídos do APK. Foram
conferidos os três layouts, mídia carregada e geometria dos slots, preservar/
preencher, temas, ajustes, persistência, vazio/recuperação e retorno de foco.
A entrada TV não contém os CTAs dos simuladores; seus recursos não fizeram
requisições externas no smoke. A inspeção do APK confirmou oito assets e versão
injetada. Evidências detalhadas e limitações ficam na issue #8 e no PR respectivo.

## 5. Controle, persistência e lifecycle da TV

Implementação I3 integrada e aceite operacional verificado na
[issue #9](https://github.com/RogerVFbr/memora/issues/9). O contrato abaixo separa
comportamentos implementados e evidências efetivamente observadas no aparelho.

### Entrada e foco

D-pad e OK seguem o encaminhamento padrão do WebView para setas e ativação do
elemento em foco. Menu, Back e teclas de mídia recebidas pela Activity são
convertidas em comandos fixos `menu`, `back`, `toggle`, `play` e `pause`, uma vez
por pressionamento; não se repetem durante key repeat. D-pad, OK, Menu, Voltar e Play/Pause foram verificados no controle correto e/ou
por injeção dos respectivos eventos ADB; não se afirma que o controle tenha
teclas físicas separadas para Play e Pause. Voltar fecha o
painel, depois os controles; na cena limpa abre os controles, preservando a
navegação de referência do laboratório.

Na entrada TV, seletores usam botões locais: esquerda/direita percorre opções,
OK escolhe a próxima e cima/baixo navega entre linhas. A bancada mantém selects
nativos. Abertura foca play/pausa; fechamento do painel restaura seu acionador,
inclusive após remontagem. Comandos de mídia fecham painéis/controles e mantêm
o viewport em foco. A Activity solicita ocultar barras do sistema, com área de renderização observada no [baseline](#3-baseline-em-leitura).

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
fisicamente desligada. A flag real foi conferida nos estados descritos nas evidências abaixo.

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

A instalação dev de I3 foi autorizada expressamente e executada em 2026-10-25,
após repetir a skill `firestick-check` e conferir o hash do APK validado.
ADB retornou `Success`; `dumpsys package` confirmou `br.com.memora.dev`,
`versionName=0.1.0-alpha.1-dev` e `versionCode=1`. A abertura posterior por
`am start` também foi autorizada e executada. O aparelho inicialmente estava
em repouso; o desenvolvedor o despertou pelo controle e confirmou o Memora na TV.

Leituras do DevTools encaminhado apenas por porta local identificaram a origem
embarcada, listeners instalados, mídia carregada e os três layouts durante
reprodução. A inspeção conferiu dimensões dos slots e `object-fit: contain` sem
distorção; captura privada da tela real foi revisada. Os valores de renderização
ficam no [baseline](#3-baseline-em-leitura). A janela nativa reportou
`KEEP_SCREEN_ON` nos estados observados e detalhados abaixo. Não houve erro
crítico observado na leitura delimitada de logs; isso não prova ausência de
falhas em cenários ainda não executados.

Uma única tecla Menu por ADB foi autorizada e enviada: abriu os controles,
focou Pausar apresentação e suspendeu o avanço na mesma cena. Nenhuma outra
tecla foi injetada. O APK recuperado em leitura do dispositivo tem o mesmo hash
do APK autorizado, descartando divergência do binário instalado.

A tentativa inicial de teste físico foi desconsiderada: o desenvolvedor informou
que estava usando o controle errado. O reteste com o controle correto confirmou
navegação, painéis e ajustes; a primeira tentativa não é defeito do aplicativo.

Após a autorização operacional consolidada no [PR #28](https://github.com/RogerVFbr/memora/pull/28),
os testes ADB verificaram abertura/fechamento de ajustes e coleções, foco inicial,
retorno aos respectivos acionadores e os três layouts com mídia carregada.
Menu suspendeu o avanço por mais de um intervalo configurado. Pause com controles
fechados removeu `KEEP_SCREEN_ON`; reprodução e menus visíveis ativaram a flag.
Home ocultou o documento, removeu a flag e manteve a cena sem avanço por mais de
um intervalo. A retomada e a reabertura após `am force-stop` retornaram pausadas,
com controles visíveis e foco em Iniciar apresentação.

Com autorização específica do desenvolvedor, a interface aplicou Galeria neutra,
6 segundos, Preencher, informações desligadas, movimento desligado e somente
Lá fora ativa. O envelope completo foi comparado antes/depois de encerrar e
reabrir o processo: todos esses valores sobreviveram. O filtro reproduziu dupla
e mosaico, sem incluir a coleção desativada. Os testes não modificaram
configurações do sistema, permissões, conectividade ou outros aplicativos.

O aceite de I3 cobre o recorte demonstrativo: não houve release, upgrade de
pacote de produção, benchmark de FPS/RAM, sessão prolongada ou integração Google.
As evidências posteriores de atualização, sessão contínua e smoke das releases
estão na [central do plano](plans/01-primeira-versao-instalavel.md#5-central-de-progresso);
consulte-a para o estado final de I5/I6. SPK-06 continua parcial.
Endereço, capturas, logs, port forwarding e diagnósticos brutos ficaram privados.
Consultar as [autorizações vigentes](../AGENTS.md#fluxo-de-trabalho-no-repositório)
antes de novas operações.


Fontes consultadas: [WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat)
e [tela ligada](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).
