# Plano — viabilidade de acesso ao Google Fotos

> Data: 2026-10-25 · Estado: aprovado para execução; G1–G3 concluídas; G4 em andamento.
> Aprovação explícita do desenvolvedor: 2026-10-25; recorte e decomposição G1–G6.
> Issues G1–G6 publicadas; integração documental pela [issue #41](https://github.com/RogerVFbr/memora/issues/41).

## 1. Objetivo e fontes

Determinar se o Memora pode acessar seleções reais do Google Fotos no Fire TV
por um caminho oficialmente suportado e oferecer acesso recorrente aceitável.
Uma conclusão negativa bem demonstrada é resultado válido da investigação;
não equivale a autorização para implementar um contorno ou iniciar o MVP.

O [PRD §11](../00-PRD.md#11-estratégia-de-entrega-e-experimentação) define os
spikes e o gate; a [§17](../00-PRD.md#17-instruções-para-execução-com-codex-cli)
define sua precedência. Requisitos, limites, segurança e riscos continuam no PRD.
O [registro Google](../04-viabilidade-google-fotos.md) concentra fontes consultadas,
protocolos de evidência e resultados futuros. O fluxo e autorizações ficam no
[AGENTS.md](../../AGENTS.md#planejamento-e-retomada-de-contexto).

## 2. Recorte aprovado

- Investigar enquadramento de uso/políticas, autorização, seleção, recuperação de
  mídia, expiração, escala e semântica de coleções.
- Executar provas pequenas no dispositivo-alvo somente após condições de acesso,
  consentimento e arquitetura de autorização estarem definidas.
- Preservar a demonstração existente como baseline. Preferir a variante de
  desenvolvimento para experimentos; preparar uma entrega assinada nova somente
  se necessária ao aceite, pelo pipeline existente, sem substituir releases.
- Usar navegador externo/celular apenas onde o fluxo oficial exigir. Credenciais
  não entram no React, logs, QR, fixtures ou documentação pública.
- Não escolher agora um backend. Se necessário, apresentar alternativa, função,
  retenção, segurança, custo, implantação e necessidade de uso cotidiano para
  decisão específica antes de contratar ou implantar infraestrutura.

Não inclui MVP, catálogo definitivo, UX final, sincronização irrestrita, cache
permanente, reprodução de vídeo/Live Photo (SPK-05), benchmark de animação
(SPK-06) ou identidade visual final. A amostra de vídeos em SPK-02 verifica
acesso/status/bytes autorizados; não comprova reprodução ou codecs.

## 3. Sequência e decisões de passagem

| Etapa | Resultado esperado | Depende de | Passagem para a próxima |
|---|---|---|---|
| G1 — Enquadramento e desenho do ensaio | Avaliação das políticas, inventário dos pré-requisitos e arquitetura candidata documentada para SPK-01 | Aprovação e integração deste planejamento | Caminho justificável para testar; dúvidas impeditivas exigem esclarecimento antes de usar dados reais |
| G2 — SPK-01: autorização no Fire TV | Prova real do fluxo apropriado à plataforma e ao escopo, com falhas/cancelamento e fronteira segura | G1 e decisões externas que ela identificar | Prova operacional positiva; bloqueio não libera G3 |
| G3 — SPK-02: seleção e recuperação | Seleção de 10 fotos + 2 vídeos, paginação real e obtenção controlada de mídia/metadados | G2 aceita; amostra consentida disponível | Seleção e acesso reproduzíveis, com tempos registrados para G4 |
| G4 — SPK-03: expiração e reabertura | Matriz real de OAuth, URL e sessão; caminho de recuperação e esforço do usuário | G3 aceita | Limites reais demonstrados; não exige resultado de acesso permanente |
| G5 — SPK-04: escala e coleções | Ensaio com álbum acima do limite por sessão, paginação e verificação de membership | G3 aceita; G4 executada antes por prioridade; acervo consentido suficiente | Limites e semântica demonstrados; falta de amostra não recebe aceite por documentação |
| G6 — Decisão de viabilidade | Síntese das evidências e decisão expressa do desenvolvedor sobre continuidade/recorte | G1–G5 com evidência suficiente ou bloqueio conclusivo antecipado | Viável, viável com limitações aprovadas, inviável no recorte ou inconclusivo |

A ordem normal é G1 → G2 → G3 → G4 → G5 → G6. G5 depende tecnicamente de G3;
G4 recebe prioridade por atacar o risco principal de recorrência. Se uma etapa
revelar impedimento conclusivo, registrar as dependentes como não executadas e
levar a evidência a G6, sem fingir conclusão dos spikes não realizados.

Aprovar este plano autoriza investigar os riscos, não aceita previamente uma
frequência de reseleção, um backend ou mudança do requisito Google Fotos.
Essas decisões serão apresentadas com evidências; não é necessário adivinhá-las
para planejar o ensaio.

## 4. Dependências e preparação externa

A demonstração está concluída no [plano anterior](01-primeira-versao-instalavel.md#5-central-de-progresso).
Seu controle/lifecycle/pipeline podem ser reaproveitados; não substituem os gates
Google. Nenhuma configuração Google Cloud, credencial, conta ou amostra pessoal
foi inspecionada ou presumida disponível nesta preparação.

G1 deve identificar projeto de teste, API, consentimento, cliente apropriado,
contas e amostras necessários, separando pré-requisito disponível de ação do
proprietário. Configuração de conta/Cloud e eventual infraestrutura externa
seguem seu escopo próprio; autonomia no Memora não é autorização irrestrita para
alterar esses serviços. Senhas não serão solicitadas na conversa.

Se a prova exigir mudar configurações globais ou outros aplicativos do Fire TV,
aplicar o AGENTS.md; a aprovação do plano não amplia a autonomia do Memora.

## 5. Central de progresso

A preparação documental é atendida pela [issue #41](https://github.com/RogerVFbr/memora/issues/41)
e não integra o contador das etapas investigativas.

**Situação em 2026-10-25:** 3 de 6 etapas concluídas. G3 verificou no alvo a amostra
completa de 10 fotos e 2 vídeos, entregue pelo PR #57. Evidência privada preservada
para G4 / #45, em preparação da matriz e observação de expirações reais.

| Etapa | Issue | Estado | PR / commit | Evidência ou pendência |
|---|---|---|---|---|
| G1 | [#42](https://github.com/RogerVFbr/memora/issues/42) | Concluída | [PR #49](https://github.com/RogerVFbr/memora/pull/49), [PR #50](https://github.com/RogerVFbr/memora/pull/50), [PR #51](https://github.com/RogerVFbr/memora/pull/51), [PR #52](https://github.com/RogerVFbr/memora/pull/52), [PR #53](https://github.com/RogerVFbr/memora/pull/53) | [Desenho e limites de passagem](../04-viabilidade-google-fotos.md#54-g1--candidato-android-preparado-para-spk-01) |
| G2 | [#43](https://github.com/RogerVFbr/memora/issues/43) | Concluída | [PR #54](https://github.com/RogerVFbr/memora/pull/54), [PR #55](https://github.com/RogerVFbr/memora/pull/55), [PR #56](https://github.com/RogerVFbr/memora/pull/56) | [Aceite operacional e limites](../04-viabilidade-google-fotos.md#62-aceite-operacional-da-g2) |
| G3 | [#44](https://github.com/RogerVFbr/memora/issues/44) | Concluída | [PR #57](https://github.com/RogerVFbr/memora/pull/57) | [Aceite operacional e âncoras temporais](../04-viabilidade-google-fotos.md#73-aceite-operacional-da-g3) |
| G4 | [#45](https://github.com/RogerVFbr/memora/issues/45) | Em andamento | — | [Matriz e condição de retomada](../04-viabilidade-google-fotos.md#8-expiração-e-reabertura-g4); fronteiras naturais ainda pendentes |
| G5 | [#46](https://github.com/RogerVFbr/memora/issues/46) | A fazer | — | Depende de G3 e amostra suficiente; executar após G4 |
| G6 | [#47](https://github.com/RogerVFbr/memora/issues/47) | A fazer | — | Decisão com evidências suficientes ou impedimento conclusivo |

### Manutenção e retomada

O detalhamento executável e os resultados ficam nas issues vinculadas acima.
Ao revisar a decomposição, aplicar a validação e publicação definidas no
AGENTS.md e atualizar imediatamente os vínculos desta central.

Ao iniciar, revisar dependências, Git e PRs pelo AGENTS.md. Atualizar estado,
evidências, bloqueio/ação necessária e próximo passo a cada mudança relevante.
Usar A fazer, Em andamento, Em revisão, Em validação, Bloqueada e Concluída.
Concluída exige integração pertinente e aceite comprovado; resultado negativo
pode concluir uma investigação, mas não comprova capacidade do produto.

Ao encerrar cada etapa, registrar na issue os resultados e limites antes do
merge, vincular a seção correspondente do registro Google, atualizar contador,
linha e histórico desta central. Não versionar evidências brutas. A expiração
observada em G4 exige registro privado de horários e condição de retomada; um
mock, relógio do dispositivo alterado ou sessão excluída não substituem esse ensaio.

Retomar com o [prompt existente](../prompts/implementar-proxima-etapa.md), indicando
este plano quando necessário. Não criar um segundo prompt com status fixos.

### Histórico

- 2026-10-25 — G3 / #44 concluída pelo [PR #57](https://github.com/RogerVFbr/memora/pull/57):
  [amostra completa e limites](../04-viabilidade-google-fotos.md#73-aceite-operacional-da-g3)
  comprovados no alvo. 3/6; próxima etapa G4 / #45 com sessão e URLs preservadas.

- 2026-10-25 — G2 / #43 concluída: [positivos, negativos e revisão de privacidade](../04-viabilidade-google-fotos.md#62-aceite-operacional-da-g2)
  comprovados no alvo; risco residual de logs de sistema aceito pelo desenvolvedor
  somente para SPK-01, conforme PRD §10. 2/6; próxima etapa técnica G3 / #44,
  com pré-requisitos de mídia pendentes e sem execução nesta rodada.

- 2026-10-25 — G2 / #43: [autorização e sessão vazia confirmadas no alvo](../04-viabilidade-google-fotos.md#61-evidência-positiva-no-fire-tv).
  Aceite parcial registrado; 1/6, G2 em validação, sem seleção de mídia ou avanço G3.

- 2026-10-25 — G1 / #42: consentimento/cliente preparados pelo proprietário e
  exportação validada; [desenho e limites de passagem](../04-viabilidade-google-fotos.md#54-g1--candidato-android-preparado-para-spk-01).
  1/6 no desenho, sem OAuth ou mídia comprovados. G2 elegível para prova isolada.

- 2026-10-25 — Preparação Cloud de G1 / #42: CLI autenticada pelo proprietário,
  projeto ativo criado e Picker API habilitada; [resultado e pendências](../04-viabilidade-google-fotos.md#preparação-cloud-executada).
  Consentimento/cliente pendentes; nenhum acesso a mídia ou aceite operacional.

- 2026-10-25 — Retomada G1 / #42 pelo Picker, por determinação do desenvolvedor.
  [Preparação e candidato OAuth](../04-viabilidade-google-fotos.md#53-g1--retomada-do-caminho-picker)
  registrados; proprietário confirmou projeto/consentimento ainda não configurados.
  Sem implementação ou aceite; 0/6. Candidatura Ambient deixa de ser próxima ação.
  Registro pelo [PR #51](https://github.com/RogerVFbr/memora/pull/51).

- 2026-10-25 — Retomada G1 / #42: investigação do TV Gallery e Ambient corrigiu
  a cobertura inicial; [evidências, candidatura preparada e retomada](../04-viabilidade-google-fotos.md#52-g1--tv-gallery-e-acesso-à-ambient-api).
  Proprietário confirmou ausência de acesso aprovado; nenhum envio externo,
  implementação ou ensaio. Registro pelo [PR #50](https://github.com/RogerVFbr/memora/pull/50).
  G1 aberta, 0/6; etapas seguintes não executadas.

- 2026-10-25 — G1 / #42: avaliação documental e inventário em leitura realizados;
  [resultado e condições de retomada](../04-viabilidade-google-fotos.md#51-g1--enquadramento-e-arquitetura-candidata).
  Sem conformidade ou fluxo operacional comprovados; etapa bloqueada, contador
  mantido em 0/6. G2 não iniciada. Registro pelo [PR #49](https://github.com/RogerVFbr/memora/pull/49), vinculado na própria issue.

- 2026-10-25: pesquisa das fontes oficiais e preparação do rascunho. Nenhuma
  configuração externa, implementação ou validação Google executada.
- 2026-10-25: desenvolvedor aprovou explicitamente o recorte e as seis etapas;
  issues [#42](https://github.com/RogerVFbr/memora/issues/42) a
  [#47](https://github.com/RogerVFbr/memora/issues/47) publicadas em ordem,
  com dependências vinculadas. Nenhum experimento iniciado.

## 6. Conclusão do plano

O plano termina com uma decisão conclusiva registrada que distingue capacidade
documentada, resultado observado e aceitação do produto. Resultado inconclusivo
mantém a etapa em validação ou bloqueada, com ação de retomada; não encerra
o plano nem libera MVP. Se não houver caminho permitido
ou recorrência aceitável, registrar o limite e o que precisaria mudar, sem
recorrer a APIs privadas, scraping ou espelhamento integral.

Mesmo uma decisão favorável neste recorte não encerra SPK-05/SPK-06 nem comprova
requisitos ainda sem teste. A implementação do MVP exige recorte aprovado em
planejamento próprio, conforme o PRD.

Rastreabilidade: OBJ-01/02/04/05/06, FR-001..004/012/013/017/018,
NFR-001/002/004/005/007/009/010/012, ADR-001/009/011/012 e riscos
R-001..005/008/011..013 do PRD. Desempenho e playback de vídeo permanecem fora.
