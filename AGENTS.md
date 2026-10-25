# Instruções para agentes — Memora

## Fonte de verdade e estado atual

- Leia integralmente [docs/00-PRD.md](docs/00-PRD.md) antes de gerar código.
  Esse é o caminho real do PRD, inclusive onde o documento menciona `PRD.md`.
- O PRD está aprovado para planejamento; implementação ainda não iniciada.
  Ainda não há comandos de build, lint ou testes configurados.
- Preserve a distinção entre `[DECIDIDO]`, `[PROPOSTO]`, `[VALIDAR]`,
  `[FUTURO]` e `[BLOQUEADOR]`. Propostas não são decisões e hipóteses precisam
  de evidência antes de orientar compromissos de arquitetura ou produto.
- Vincule tarefas e validações aos IDs do PRD: OBJ, FR, NFR, SPK, ADR e riscos.
  Registre novas decisões, resultados e baselines no PRD e em `docs/`.

## Produto e limites de escopo

- Memora transforma a TV em moldura digital cinematográfica com fotos e vídeos
  do Google Fotos, composições dinâmicas e operação por controle remoto.
- Alvo decidido: Amazon Fire TV Stick 4K Max, 2ª geração (2023), Fire OS 8,
  baseado em Android 11. Distribuição por APK e instalação local via ADB.
- Google Fotos é a fonte obrigatória. Não exigir migração para NAS, Drive ou
  biblioteca local, nem duplicação permanente do acervo. Cache é transitório.
- Priorize P0: slideshow durante acesso válido, foto individual, dois retratos,
  mosaico simples, transições, menu D-pad, preferências, cache e recuperação.
- Vídeos e Live Photos têm prioridade P1; o recorte final depende da POC.
  Não ampliar o MVP com recursos P2 ou futuros sem definição de escopo.
- Não introduzir publicação em loja, backend SaaS obrigatório, microserviços,
  daemon permanente ou screensaver global como requisitos do MVP.

## Gate de viabilidade antes do MVP

- Comece por SPK-01 a SPK-04: OAuth compatível com o escopo Picker, seleção,
  expiração/reacesso e semântica de álbuns/escala. Não iniciar a UX final antes
  de decidir a viabilidade e registrar uma experiência recorrente aceitável.
- OAuth da TV e QR Code `pickerUri` são etapas distintas. Não presumir que
  Device Authorization Grant aceite `photospicker.mediaitems.readonly`.
- Google Photos Picker API e Android Photo Picker são tecnologias distintas;
  o componente Android não substitui a integração REST exigida.
- Não prometer catálogo irrestrito, sincronização automática de álbuns nem
  acesso permanente a partir de IDs persistentes, catálogo ou cache local.
- Diferencie álbum do Google Fotos, seleção Picker temporária e coleção local.
  Trate expiração da sessão, do `baseUrl` e do OAuth separadamente.
- Valide na documentação oficial atual os limites e políticas antes de decisões
  sobre OAuth, sessões, URLs, álbuns e retenção. O PRD registra 2.000 itens por
  sessão e cerca de 60 minutos para `baseUrl`; não tratar como garantias eternas.
- Registre cada bloqueador em `docs/` com fonte oficial, procedimento reproduzível,
  resultado e impacto. Não contornar restrições com scraping ou APIs privadas
  sem decisão expressa e análise de riscos.
- Execute também SPK-05 e SPK-06 para vídeo/Live Photo e compatibilidade e
  desempenho no hardware. Mocks não comprovam comportamento real da plataforma.

## Arquitetura e organização

- Decidido: Kotlin nativo e apresentação HTML/CSS/JS em WebView, num APK.
  Frontend estático incorporado ao aplicativo, sem servidor remoto de renderização.
- Proposto: React + TypeScript + Vite, Tailwind, GSAP/Flip e React Photo Album;
  Media3/ExoPlayer para vídeo e Room para catálogo. Justifique adoção conforme POC.
- Valide WebView/Chromium real antes de fixar Tailwind v4; v3.4 é fallback proposto.
  Valide codecs e integração de surfaces antes de comprometer o player.
- Layout proposto: `android/`, `web/`, `docs/` e `tools/`. Esses diretórios de
  implementação não devem ser descritos como existentes antes de serem criados.
- Separe connector/autorização, catálogo/preferências, preparação de mídia,
  orquestração, apresentação e lifecycle. Mantenha uma fonte de verdade de playback.
- A bridge Kotlin/JS deve ser mínima, tipada, auditável e restrita a origem confiável.
  Tokens e downloads autenticados ficam no nativo; não expor credenciais ou
  `baseUrl` autenticado no DOM nem permitir navegação/URLs arbitrárias.

## Reprodução, recursos e experiência de TV

- Preserve os estados do PRD: `NEEDS_AUTH`, `PICKING`, `PREPARING`, `PLAYING`,
  `MENU_VISIBLE`, `PAUSED`, `SUSPENDED` e `NEEDS_RESELECT`.
- Implemente foco e navegação D-pad, OK/Menu e Voltar. Após onboarding, a operação
  cotidiana não deve depender de mouse, teclado ou celular durante sessão válida.
- Ao suspender a Activity, interrompa player, downloads e requisições dispensáveis.
  Controle `FLAG_KEEP_SCREEN_ON` conforme reprodução/visibilidade; não inferir
  presença humana nem desligamento físico da TV a partir dessa flag.
- Use buffer deslizante, downsampling por slot, limites de RAM/disco e concorrência,
  deduplicação e descarte LRU. Não baixar a biblioteca inteira.
- Polling apenas durante seleção Picker pendente, respeitando `pollingConfig`.
  Use retries limitados, `Retry-After` e backoff com jitter para evitar tempestades.
- Prefira animações por `transform` e `opacity`. Meça fluidez, memória e latência
  no Fire Stick antes de fixar metas ou limites quantitativos.
- Não invente GPS/localização nem fuso de captura. Diferencie relógio atual da TV
  de data/hora da mídia e indique a origem de qualquer enriquecimento.
- Registre como questões abertas as escolhas ainda não fechadas no PRD, como
  reprodução sob menu, áudio, duração padrão e política de crop.

## Segurança e privacidade

- Nunca versione credenciais, tokens, segredos de ambiente ou fotos pessoais.
  Não embutir client secret confidencial no APK nem capturar senha Google.
- Use OAuth/PKCE com cliente apropriado e escopo mínimo, após validação do fluxo.
  Proteja material persistente com criptografia e Android Keystore quando aplicável.
- Não transmitir tokens por QR Code exposto. Se pareamento exigir serviço remoto,
  documente e reavalie segurança, custo e deploy antes de aprová-lo.
- Não registrar fotos, tokens, GPS, `pickerUri` ou URLs de mídia em logs/Logcat.
  Telemetria deve ser local, desativável e sem upload externo por padrão.
- Não enviar fotos a servidores próprios. Respeite políticas Google de cache e
  retenção; ofereça logout, limpeza de dados/cache e reset de seleção.
- Use mídia sintética ou autorizada em testes; amostras pessoais reais exigem
  consentimento e permanecem fora de fixtures versionadas e CI.

## Gitflow e convenção de branches

- Use Gitflow: `main` representa versões estáveis e `develop` integra o trabalho
  da próxima versão. Sempre trabalhe em uma branch de tarefa, nunca diretamente
  em `main` ou `develop`.
- Crie branches de tarefa a partir de `develop`, no formato `<tipo>/<descricao>`,
  com descrição curta em inglês, minúsculas e palavras separadas por hífen.
- Convenção de nomes baseada em Conventional Branch, estendida com os tipos
  usados pelo projeto: `feat/`, `fix/`, `chore/`, `docs/`, `test/`, `refactor/`,
  `perf/`, `build/` e `ci/`. Use `chore`, não `chroe`.
- Exemplos: `feat/portrait-pair`, `fix/picker-timeout`, `docs/oauth-spike`,
  `test/cache-eviction` e `chore/gradle-setup`.
- Integre branches de tarefa em `develop` por pull request, após revisão e
  verificações pertinentes. Criar a branch não autoriza automaticamente merge
  ou envio ao remoto.
- Prepare versões em `release/<versao>`, a partir de `develop`; ao concluir a
  release autorizada, integre em `main` e `develop` e marque `main` com tag `vX.Y.Z`.
- Correções urgentes de versão publicada usam `hotfix/<descricao>`, a partir de
  `main`, com integração em `main` e `develop` após validação e autorização.
- Antes de trocar de branch, confira alterações pendentes e preserve o trabalho
  existente; não faça stash, descarte ou commit automático de arquivos do usuário.
- Referências: [Conventional Branch](https://conventionalbranch.org/) e
  [Gitflow](https://git-flow.sh/workflows/gitflow/).

## Fluxo de trabalho e validação

- Comunique-se em português brasileiro, com decisões e limitações objetivas.
- Antes de editar, leia os arquivos relacionados e confira o Git. Preserve trabalho
  do usuário e mantenha incrementos pequenos, testáveis e instaláveis por ADB.
- Registre no `README.md` somente pré-requisitos e comandos reais introduzidos
  para build frontend/Gradle, execução, testes e instalação. Não inventar comandos.
- Quando houver código, valide contratos Google, paginação, estados, limites,
  cache e retry em Kotlin; composição e eventos do controle no frontend.
- Use mocks para integração e smoke test no Fire Stick real. Inclua expiração,
  revogação OAuth, offline/429/5xx, lifecycle, vídeo falho e cache saturado.
- Para alterações apenas documentais, revise conteúdo e execute `git diff --check`.
  Relate o que mudou, as verificações executadas e o que não pôde ser validado.
- Não execute ações destrutivas nem publique ou envie alterações ao remoto
  sem autorização do usuário.
- Atualize este arquivo quando decisões, estrutura ou fluxos do projeto mudarem.
