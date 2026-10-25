# Host Android e baseline de plataforma

> Evidência de I1 / [#7](https://github.com/RogerVFbr/memora/issues/7), coletada em 2026-10-25.

Consulte o [recorte aprovado](plans/01-primeira-versao-instalavel.md#2-recorte-aprovado)
antes de ampliar este host. O [PRD §6](00-PRD.md#6-arquitetura-lógica-proposta)
define a arquitetura do produto; aqui ficam as escolhas concretas deste incremento.
Comandos de operação têm fonte única no [README](../README.md#host-android-local).

## 1. Host e fronteira

Projeto Gradle independente em `apps/android`, com uma Activity Kotlin e sem
Compose, serviço de fundo ou bridge JavaScript. `br.com.memora` é a identidade
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
conexões, frames ou formulários. Não há interfaces nativas expostas ao JS.

A Activity encaminha pausa/retomada ao WebView e o destrói ao encerrar. A fonte de playback da demonstração é o controlador React compartilhado;
o avanço é suspenso sob menus e com documento oculto. A versão I2 não usa
`KEEP_SCREEN_ON`. O contrato nativo de visibilidade e a política de tela ligada
são trabalho da I3; `WebView.onPause()` sozinho não comprova suspensão dos timers JavaScript.

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
