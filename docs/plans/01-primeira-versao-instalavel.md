# Plano — primeira versão instalável e validação da entrega

> Data: 2026-10-25 · Objetivo e recorte aprovados; I1 e I2 integradas; próximas etapas pendentes.
> Aprovação: recomendações aceitas pelo desenvolvedor, com artefatos em `builds/`
> na raiz do repositório e versionamento SemVer.
> Estado do plano: aprovado para execução; I1 e I2 integradas; próximas etapas pendentes.

## 1. Objetivo e fontes de verdade

Entregar uma versão demonstrativa instalável do Memora, reaproveitando a
experiência do protótipo, e comprovar o ciclo de implementação, revisão,
empacotamento, publicação, instalação e atualização no dispositivo-alvo.

Este plano concentra sequência, convenções de entrega e critérios deste
incremento. Consulte o [PRD](../00-PRD.md#11-estratégia-de-entrega-e-experimentação)
para o enquadramento nos gates do produto, o
[documento visual](../01-identidade-visual-e-ux.md#4-arquitetura-da-experiência)
para a experiência de referência e o
[AGENTS.md](../../AGENTS.md#desenvolvimento-orientado-por-issues) para o fluxo de trabalho.
Comandos executáveis serão acrescentados ao README somente quando implementados
e verificados; os passos abaixo ainda são planejamento.

## 2. Recorte aprovado

| Decisão | Entrega desta versão |
|---|---|
| Acervo | Mesmas fotos e coleções demonstrativas locais do protótipo |
| Superfícies | Apresentação, três composições, controles, coleções e ajustes |
| Bancada | Página externa do laboratório e simuladores continuam restritos ao browser |
| Aparência | Dois temas selecionáveis; Cinema acolhedor como padrão inicial |
| Entrada | Reprodução pausada, controles visíveis, inclusive após reabertura nesta versão |
| Identificação | Indicação discreta de versão demonstrativa nos controles ou informações do app |
| Entrega | APK assinado, GitHub Release, checksum e instalação local por ADB |
| Prova de evolução | Primeira instalação e atualização posterior preservando preferências |

Não entram OAuth, Picker, vídeos, Live Photos, catálogo de mídia pessoal ou
sincronização. A arquitetura deve permitir evoluir para esses recursos sem
apresentar simulações como integração funcional. Este recorte é uma etapa de
engenharia da aplicação, não a conclusão do MVP do PRD.

## 3. Estratégia técnica

1. Inspecionar toolchain local, projeto existente e dispositivo em leitura antes
   de fixar versões de JDK, SDK, Gradle, Kotlin, dependências e target do frontend.
2. Preparar o host Android/Kotlin e frontend estático embarcado, conforme a
   [arquitetura do PRD](../00-PRD.md#6-arquitetura-lógica-proposta). Não depender
   do servidor Vite durante execução no Fire Stick.
3. Extrair a apresentação do protótipo para componentes reaproveitáveis, mantendo
   o laboratório utilizável no browser. Evitar duas cópias divergentes dos mesmos
   componentes, tokens, fixtures e regras de interação.
4. Definir uma fonte única de estado de reprodução e a fronteira com lifecycle
   nativo. Catálogo demonstrativo deve ser um provedor substituível. Não implantar
   bridge genérica ou métodos nativos sem necessidade demonstrada.
5. Adaptar foco e entrada ao controle físico, inclusive seletores de ajustes que
   hoje dependem de controles HTML nativos; avaliar no WebView real.
6. Implementar preferências persistentes e versionadas, com comportamento de
   leitura/migração verificável. A tecnologia e a localização da persistência
   serão escolhidas na issue técnica; sua integridade no upgrade é critério obrigatório.
7. Integrar visibilidade da Activity e reprodução; validar suspensão e retomada
   consciente, sem avanço de fila invisível. O retorno reapresenta os controles
   com reprodução pausada, preservando os ajustes salvos.

O host está em `apps/android`, a entrada TV em `apps/tv` e a apresentação
compartilhada em `packages/presentation`, preservando `apps/prototype`. Consulte a
[fronteira concreta](../02-host-android-e-baseline.md#1-host-e-fronteira) antes de
alterar a organização ou o empacotamento.

A validação deve registrar WebView efetivo, resolução de renderização, versão do
sistema, erros e comportamento observado. Não prometer suporte a efeitos ou
desempenho com base exclusiva no browser desktop. Consulte
[SPK-06](../00-PRD.md#fase-0--spike-técnico-gate-de-viabilidade-obrigatório)
antes de declarar cobertura do spike: a entrega deste plano pode fornecer
evidência parcial, sem encerrar automaticamente seu escopo.

## 4. SemVer, assinatura e artefatos

### Identidade de versão

Usar SemVer `MAJOR.MINOR.PATCH`, com identificador de pré-lançamento para esta
etapa. Sequência inicial planejada:

| Entrega | Versão visível / `versionName` | Tag | `versionCode` inicial planejado |
|---|---|---|---|
| Primeira instalação demonstrativa | `0.1.0-alpha.1` | `v0.1.0-alpha.1` | 1 |
| Prova de atualização | `0.1.0-alpha.2` | `v0.1.0-alpha.2` | 2 |

São versões da aplicação Android; `0.1.0` no pacote atual do protótipo não
representa uma release Android existente. Antes de reservar uma versão, conferir
tags, releases e eventual instalação prévia. Ajustar os valores se já utilizados.

Manter a versão em uma fonte de configuração versionada, definida na implementação;
derivar dela nome visível e metadados de entrega. O contador Android é inteiro
monotônico por entrega instalável e não deve depender da comparação textual dos
rótulos SemVer. Release posterior precisa de código superior ao instalado.

Durante `0.x`, os contratos ainda são experimentais. Nas notas, declarar mudanças
em preferências, formato persistido e comandos da aplicação. Ao estabilizar a
interface pública, adotar os incrementos de major para incompatibilidade, minor
para extensão compatível e patch para correção compatível. Não usar metadados
`+build` como mecanismo de ordenação de atualização Android.

Cada pré-release publicada é imutável. Correções recebem novo identificador;
não substituir APK de uma versão publicada por bytes diferentes.
Referências: [SemVer 2.0.0](https://semver.org/lang/pt-BR/) e
[versionamento Android](https://developer.android.com/studio/publish/versioning).

### Diretório de entrega

Salvar os **artefatos entregáveis** em
`/Volumes/ACASISHD1/repos/memora/builds/<semver>/`.
Em scripts, resolver a raiz do checkout e usar `builds/`, sem fixar o caminho
absoluto deste computador. Caches e intermediários podem permanecer nos diretórios
normais das ferramentas. Estrutura planejada, ainda não produzida:

```text
builds/
└── 0.1.0-alpha.1/
    ├── memora-0.1.0-alpha.1.apk
    ├── SHA256SUMS
    ├── build-info.json
    └── release-notes.md
```

`build-info.json` deverá conter versão, código Android, application ID, commit de
origem, horário UTC, versões das ferramentas e identificação pública do
certificado. Não incluir caminhos privados, credenciais nem diagnósticos pessoais.
Registrar checksum do APK assinado final; a cópia publicada deve ter os mesmos bytes.

Adicionar `/builds/` ao `.gitignore` na issue de empacotamento. APKs são anexos da
Release, não arquivos de código versionados. Evidências técnicas brutas continuam
privadas conforme os guardrails existentes.

### Assinatura e atualização

Escolher application ID estável antes da primeira instalação e registrar a escolha
na configuração do aplicativo. Usar a mesma identidade de pacote e certificado
de assinatura nas duas entregas. Preparar chave persistente de release e backup
seguro, fora de Git e dos anexos; definir sua custódia antes de publicar a primeira.
Não usar chave debug efêmera para a sequência de releases.

A segunda entrega deve trazer incremento de versão/código verificável na UI e nos
metadados, passando pelo mesmo pipeline. Não exige recurso novo ou alteração de
esquema artificial: o objetivo é testar atualização real sem desinstalar ou limpar
dados. Mudanças que exijam migração devem ter teste específico se surgirem.
Referência: [assinatura Android](https://developer.android.com/studio/publish/app-signing).

## 5. Central de progresso

Esta seção é a fonte de verdade do progresso consolidado. Escopo detalhado,
critérios de aceite e validações específicas ficam nas issues vinculadas; não
manter uma segunda cópia integral desses textos no repositório.

**Situação em 2026-10-25:** 2 de 6 etapas concluídas. I1 e I2 integradas e aceitas.
I3 / #9 em validação; aceite de hardware pendente.

A preparação documental deste plano, da skill e do prompt de retomada é atendida
pela [issue #13](https://github.com/RogerVFbr/memora/issues/13); não integra o
contador das seis etapas de implementação.

| Etapa | Issue de implementação | Depende de | Status | PR / commit integrado | Release | Evidências e pendências |
|---|---|---|---|---|---|---|
| I1 — Host Android e baseline | [#7](https://github.com/RogerVFbr/memora/issues/7) | Integração do plano | Concluída | [#21](https://github.com/RogerVFbr/memora/pull/21) / [58efcf4](https://github.com/RogerVFbr/memora/commit/58efcf4d2b2e01230dc997103e8ad381b785eb17) | Não se aplica | Build limpo, dois testes, lint e inspeção do APK aprovados; [baseline em leitura](../02-host-android-e-baseline.md#3-baseline-em-leitura) parcial com ação de resolução; sem instalação ou aceite de hardware |
| I2 — Apresentação compartilhada e embarcada | [#8](https://github.com/RogerVFbr/memora/issues/8) | I1 / #7 | Concluída | [#23](https://github.com/RogerVFbr/memora/pull/23) / [2cf9aef](https://github.com/RogerVFbr/memora/commit/2cf9aefc7431696cc0df8bf00bf89b8090b13d4f) | Não se aplica | [Verificações e limitações](https://github.com/RogerVFbr/memora/issues/8#issuecomment-6401455790); hardware permanece em I3 |
| I3 — Controle, persistência e lifecycle | [#9](https://github.com/RogerVFbr/memora/issues/9) | I2 / #8 | Em validação | — | Não se aplica | Código e testes locais preparados; [aceite de hardware pendente](../02-host-android-e-baseline.md#5-controle-persistência-e-lifecycle-da-tv), começando por instalação autorizada |
| I4 — Empacotamento, assinatura e SemVer | [#10](https://github.com/RogerVFbr/memora/issues/10) | I3 / #9 | A fazer | — | Não se aplica | Custódia da chave e comandos de entrega pendentes |
| I5 — Primeira instalação da release | [#11](https://github.com/RogerVFbr/memora/issues/11) | I4 / #10 | A fazer | — | A produzir | Exige obtenção do anexo, instalação e smoke no hardware |
| I6 — Atualização preservando preferências | [#12](https://github.com/RogerVFbr/memora/issues/12) | I5 / #11 | A fazer | — | A produzir | Exige upgrade real e matriz de preferências antes/depois |

### Como manter o progresso

Consultar esta seção antes de iniciar uma etapa e aplicar o
[fluxo orientado por issues](../../AGENTS.md#desenvolvimento-orientado-por-issues).
Para uma nova janela, copiar o [prompt de próxima etapa](../prompts/implementar-proxima-etapa.md).
A seleção e retomada seguem o [AGENTS.md](../../AGENTS.md#planejamento-e-retomada-de-contexto);
o prompt recupera o estado desta central e das issues, sem manter outra lista de tarefas.
Atualizar status ao iniciar trabalho, abrir revisão, identificar bloqueio ou
concluir a etapa. Estados usados: **A fazer**, **Em andamento**, **Em revisão**,
**Em validação**, **Bloqueada** e **Concluída**. Registrar o motivo e a ação de
resolução quando houver bloqueio; uma dependência futura ainda não iniciada
permanece A fazer.

Ao concluir cada issue:

1. Conferir os critérios de aceite na própria issue e registrar os resultados
   detalhados, verificações e limitações nela ou no PR pertinente.
2. Atualizar sua linha nesta tabela com status, links de PR/commit e, quando
   aplicável, tag/Release. Vincular evidências sanitizadas em vez de copiar logs.
3. Registrar resultado e pendências resumidos. Trabalho fora do aceite deve
   apontar para uma issue de continuação; aceite incompleto não recebe Concluída.
4. Atualizar o contador, a data e a próxima etapa; acrescentar um registro de
   conclusão ao histórico abaixo com links para as fontes de evidência.
5. Incluir a atualização documental na entrega da issue. Se a evidência surgir
   depois do merge do código, registrar a atualização documental complementar
   pelo fluxo normal antes de encerrar a issue.

**Concluída** pressupõe entrega integrada e aceite atendido. Nas etapas I5/I6,
merge ou tag isoladamente não bastam: a validação operacional também precisa
estar concluída. Não encerrar automaticamente essas issues no PR que apenas
prepara a versão quando ainda faltam instalação, publicação ou upgrade.

A integração da fonte precede a tag da entrega; um build pode ser executado sobre
o commit integrado sem editar arquivos em `main`. Instalações de desenvolvimento
usadas em I3 não substituem o aceite da primeira identidade de release em I5.

### Histórico de conclusões

- **2026-10-25 — I1 / #7:** host Android integrado pelo [PR #21](https://github.com/RogerVFbr/memora/pull/21),
  build debug e verificações locais aprovados. [Registro de aceite e limitações](https://github.com/RogerVFbr/memora/issues/7#issuecomment-6401346065);
  [baseline técnico](../02-host-android-e-baseline.md#3-baseline-em-leitura) parcial,
  com repetição de foreground/renderização pendente para a validação real em I3.
  Sem release ou instalação nesta etapa. Próxima etapa I2 / #8 não iniciada.

- **2026-10-25 — I2 / #8:** apresentação compartilhada e frontend TV integrados pelo
  [PR #23](https://github.com/RogerVFbr/memora/pull/23), com builds e smoke estático
  aprovados. [Evidências e limitações](https://github.com/RogerVFbr/memora/issues/8#issuecomment-6401455790).
  Sem instalação; próxima etapa I3 / #9.

## 6. Pipeline de publicação e validação

Para cada entrega, construir localmente a partir de um commit integrado, com
checkout limpo e dependências fixadas. Verificar tipos/testes pertinentes,
assets embarcados, assinatura, versão e checksum antes de disponibilizar artefatos.

Preparar a GitHub Release como rascunho, com tag apontando à origem identificada,
APK, checksum e metadados não sensíveis. Recuperar o APK anexado na máquina local
e conferir seus bytes antes de usá-lo no dispositivo. Isso exercita o caminho de
publicação e obtenção do artefato. Após o smoke aprovado, publicar a Release como
pré-lançamento, registrando resultados e limitações. Se falhar, manter o rascunho
e registrar a correção; não apresentar a entrega como validada.

O teste inicial verifica instalação, abertura, reconhecimento no launcher,
orientação e tela inteira, três composições, temas, foco, ajustes e reabertura.
O teste de atualização escolhe previamente valores não padrão, atualiza sem
desinstalar, verifica a nova versão e confere cada preferência preservada.
Repetir o smoke essencial após a atualização.

Registrar uma sessão contínua para observar troca de cenas, recortes,
travamentos e uso de recursos. Sua duração e baseline devem ser acordados antes
do ensaio a partir do primeiro resultado no hardware; não inventar metas de
FPS/RAM nem declarar estabilidade prolongada a partir de um smoke curto.

Todas as operações de hardware ficam sujeitas à
[skill e às autorizações de dispositivo](../../AGENTS.md#fluxo-de-trabalho-no-repositório).
Este plano não executa nem autoriza antecipadamente uma instalação, abertura,
encerramento ou alteração de estado no Fire Stick. Seguir o procedimento vigente
no momento de cada operação. Não há dependência da conectividade do aparelho
para escrever ou revisar este plano.

## 7. Evidências e conclusão do incremento

| Evidência | Onde registrar |
|---|---|
| Comandos reais de build, teste, assinatura e publicação | README, após implementação; PR informa comandos efetivamente executados e resultados |
| Correspondência versão, tag, commit, certificado e hash | Metadados de cada entrega e anexos da Release |
| Resultado da instalação e da atualização | Issues I5/I6 e notas das respectivas releases, sem dados privados |
| Capturas, logs e dados brutos do dispositivo | Diretório privado local conforme regras existentes |
| Compatibilidade e baseline de plataforma | Evidências técnicas em `docs`, referenciadas no PRD, sem endereço ou informações pessoais |

Concluir este incremento somente quando as duas entregas forem obtidas pelo
caminho publicado, instaladas/atualizadas e verificadas, com ajustes preservados
e limitações explícitas. Build bem-sucedido isoladamente não encerra o plano.

Rastreabilidade principal: OBJ-09, ADR-002..004, FR-006..011 e FR-018 na versão
demonstrativa; NFR-002, NFR-005, NFR-008 e NFR-011 nos limites efetivamente
verificados. Não registrar como atendidos os requisitos de acesso Google nem
declarar concluído o SPK-06 sem suas evidências específicas.

## 8. Pendências de preparação e estado atual

Objetivo, recorte, temas, entrada, publicação, prova de atualização, diretório de
artefatos e uso de SemVer estão aprovados. O host e o build debug foram preparados na I1. Consulte a
[central de progresso](#5-central-de-progresso) para o estado atual das seis etapas.

Toolchain, identidade de pacote e consultas de plataforma da I1 estão no
[registro técnico](../02-host-android-e-baseline.md#2-toolchain). Pendências a resolver
nas etapas seguintes: validação do WebView com Activity visível, contrato mínimo
nativo, localização da persistência, custódia da chave de release,
duração do ensaio e validação da apresentação compartilhada no hardware. São decisões técnicas ou
evidências da execução; não bloqueiam este registro do plano.

Aprovar este plano não equivale à comprovação dos gates Google. Antes de ampliar
o acervo demonstrativo para conteúdo real, retomar a
[Fase 0 do PRD](../00-PRD.md#fase-0--spike-técnico-gate-de-viabilidade-obrigatório).
