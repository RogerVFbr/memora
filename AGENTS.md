# Instruções para agentes — Memora

## Manutenção da documentação

- Ao criar ou atualizar qualquer documentação, verifique se a informação já
  existe no PRD ou em outro documento do repositório.
- Mantenha cada informação em uma única fonte de verdade, escolhida conforme
  o assunto. Nos demais arquivos, substitua a repetição por um link para o
  arquivo e a seção específica, acompanhado de uma instrução sobre quando
  consultá-la.
- Documentos complementares devem acrescentar detalhes próprios e referenciar
  as definições existentes, sem copiar requisitos, decisões ou procedimentos.
- Ao mover conteúdo ou renomear seções, atualize as referências afetadas e
  confira se os links continuam apontando para o ponto correto.
- Aplique esse procedimento também a futuras documentações e às atualizações
  deste `AGENTS.md`.

## Fonte de verdade e consulta ao PRD

O [PRD](docs/00-PRD.md) concentra estado do produto, requisitos, decisões,
hipóteses, riscos e plano de validação. Esse é o caminho real do documento,
inclusive onde ele menciona `PRD.md`. Este arquivo concentra regras de trabalho
no repositório; não replique aqui o conteúdo do PRD.

Use o roteiro abaixo para localizar as instruções aplicáveis à tarefa. Ele não
substitui a leitura integral exigida na seção 17 antes de gerar código.

| Quando consultar | Ponto do PRD |
|---|---|
| Ao iniciar uma tarefa ou interpretar o status e a força de uma afirmação | Cabeçalho e [Legenda e regras de interpretação](docs/00-PRD.md#legenda-e-regras-de-interpretação) |
| Antes de definir escopo, prioridades, dispositivo-alvo ou critérios de aceite | [§1 — Resumo executivo](docs/00-PRD.md#1-resumo-executivo), [§2 — Problema, motivação e objetivos](docs/00-PRD.md#2-problema-motivação-e-objetivos) e [§4 — Requisitos funcionais](docs/00-PRD.md#4-requisitos-funcionais) |
| Antes de planejar implementação ou UX final, executar spikes ou avaliar o gate de viabilidade | [§11 — Estratégia de entrega e experimentação](docs/00-PRD.md#11-estratégia-de-entrega-e-experimentação) e [§17 — Instruções para execução com Codex CLI](docs/00-PRD.md#17-instruções-para-execução-com-codex-cli) |
| Antes de decidir sobre OAuth, Picker, sessões, URLs, limites ou álbuns | [§3.1 — Google Photos Picker API](docs/00-PRD.md#31-google-photos-picker-api), [§3.2 — OAuth e QR Code](docs/00-PRD.md#32-oauth-e-qr-code-bloqueador), [§4.1 — Semântica de álbuns e coleções](docs/00-PRD.md#41-semântica-de-álbuns-e-coleções-validar) e §17, itens 3–5; fontes na [§16](docs/00-PRD.md#16-referências-e-leitura-complementar) |
| Antes de escolher dependências, organizar módulos/diretórios ou alterar a bridge e o player | [§3.3 — Fire OS e UI](docs/00-PRD.md#33-fire-os-e-ui), [§6 — Arquitetura lógica proposta](docs/00-PRD.md#6-arquitetura-lógica-proposta), [§12 — Estratégia de desenvolvimento, testes e deploy](docs/00-PRD.md#12-estratégia-de-desenvolvimento-testes-e-deploy) e [§13 — ADRs resumidos](docs/00-PRD.md#13-adrs-resumidos) |
| Antes de alterar navegação, reprodução, lifecycle ou composição visual | [§4.2 — Fluxo de interação](docs/00-PRD.md#42-fluxo-de-interação-proposto), [§7 — Modelo de estados e ciclo de vida](docs/00-PRD.md#7-modelo-de-estados-e-ciclo-de-vida) e [§8 — Algoritmo de apresentação visual](docs/00-PRD.md#8-algoritmo-de-apresentação-visual) |
| Antes de alterar cache, rede, retries, métricas, desempenho ou custos | [§5 — Requisitos não funcionais](docs/00-PRD.md#5-requisitos-não-funcionais) e [§9 — Cache, política de rede e custos](docs/00-PRD.md#9-cache-política-de-rede-e-custos) |
| Antes de manipular credenciais, mídia, metadados pessoais, logs ou dados persistentes | [§8.3 — Metadados de apresentação](docs/00-PRD.md#83-metadados-de-apresentação), [§9.2 — Telemetria local mínima](docs/00-PRD.md#92-telemetria-local-mínima), [§10 — Segurança e privacidade](docs/00-PRD.md#10-segurança-e-privacidade) e guardrails da §12 |
| Ao planejar testes, selecionar amostras ou validar no hardware | §11 (SPK-01 a SPK-06) e §12 (testes, amostras consentidas e QA) |
| Antes de assumir uma resposta de produto, encerrar uma validação ou registrar uma decisão | [§14 — Registro de riscos e questões abertas](docs/00-PRD.md#14-registro-de-riscos-e-questões-abertas), [§15 — Matriz de rastreabilidade resumida](docs/00-PRD.md#15-matriz-de-rastreabilidade-resumida) e §17 (IDs, evidências, atualização do PRD e baselines) |

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

## Fluxo de trabalho no repositório

- Comunique-se em português brasileiro, com decisões e limitações objetivas.
- Antes de editar, leia os arquivos relacionados e confira o Git. Preserve o
  trabalho existente. Para o fluxo de incrementos e validação, consulte a §17 do PRD.
- Sempre que a tarefa depender da presença ou conectividade do Fire Stick,
  leia e execute a skill local
  [firestick-check](.agents/skills/firestick-check/SKILL.md) para verificar se o
  dispositivo está disponível, conectado e autorizado via ADB. Faça essa
  verificação antes de deploy, instalação, testes no hardware ou diagnóstico;
  não presuma conectividade com base em uma sessão anterior. Mantenha endereço,
  capturas e diagnósticos fora dos arquivos versionados.
- Operações de leitura no Fire Stick estão autorizadas sempre que necessárias,
  sem pedir autorização explícita, desde que não alterem o estado do dispositivo.
- Nenhuma alteração no Fire Stick pode ser feita sem autorização explícita do
  desenvolvedor. Antes de cada operação que altere o dispositivo, descreva a
  ação pretendida, peça autorização novamente e aguarde a resposta; autorizações
  anteriores não dispensam essa nova confirmação. Isso inclui instalar ou
  atualizar APKs, desinstalar aplicativos, alterar configurações, gravar ou
  excluir dados, iniciar ou encerrar aplicativos e reiniciar o dispositivo.
  Essa regra também se aplica a ações previstas em skills, scripts e testes.
- Registre no `README.md` somente pré-requisitos e comandos reais introduzidos
  para build, execução, testes e instalação. Não invente comandos nem descreva
  diretórios propostos no PRD como existentes antes de criá-los.
- Mantenha amostras pessoais fora de fixtures versionadas e CI; consulte a §12
  do PRD antes de selecionar mídia para testes.
- Para alterações apenas documentais, revise conteúdo e execute `git diff --check`.
  Relate o que mudou, as verificações executadas e o que não pôde ser validado.
- Não execute ações destrutivas nem publique ou envie alterações ao remoto
  sem autorização do usuário.
- Ao mudar regras de trabalho, atualize este arquivo. Ao mudar conteúdo de produto
  ou técnico, atualize o PRD conforme a §17 e ajuste as referências daqui se necessário.
