# Memora — identidade visual e laboratório de experiência

> Data: 2026-10-25 · Estado: conceito em avaliação, protótipo local implementado.
> A aprovação desta etapa autoriza explorar e testar a experiência no navegador;
> não aprova uma identidade definitiva nem a implementação da UX final no APK.

## 1. Papel deste documento

Este documento é a fonte de verdade das propostas visuais, do recorte do
protótipo e das decisões do debate de design. Consulte o
[PRD, legenda](00-PRD.md#legenda-e-regras-de-interpretação) para interpretar os
marcadores de decisão. Requisitos, limitações da integração, arquitetura do
produto e gates permanecem no PRD; aqui se detalha como explorar sua apresentação.

Antes de ampliar o escopo, consulte [PRD §4](00-PRD.md#4-requisitos-funcionais),
[§8](00-PRD.md#8-algoritmo-de-apresentação-visual) e
[§11](00-PRD.md#11-estratégia-de-entrega-e-experimentação).
Os comandos reais de operação ficam no [README](../README.md#protótipo-visual-local).

Para a evolução aprovada do conceito para uma versão demonstrativa no dispositivo,
consulte o [plano da primeira versão instalável](plans/01-primeira-versao-instalavel.md#2-recorte-aprovado).
O plano define o novo recorte; a aprovação inicial registrada neste documento
continua sendo o histórico da exploração no browser.

## 2. Registro de decisões e hipóteses

| ID | Estado | Definição | Motivo / condição |
|---|---|---|---|
| VIS-001 | [DECIDIDO] | Começar por definição visual e validação de conceito em browser local | Feedback rápido antes da integração nativa |
| VIS-002 | [DECIDIDO] | Implementar o laboratório em `apps/prototype` e registrar decisões em `docs` na raiz | Caminhos definidos pelo desenvolvedor |
| VIS-003 | [DECIDIDO] | Usar dados mock e mídia demonstrativa nesta etapa | Testar experiência independentemente da autorização do acervo |
| VIS-004 | [PROPOSTO] | Comparar Cinema acolhedor e Galeria neutra com o mesmo conteúdo | Escolher linguagem visual por comparação concreta |
| VIS-005 | [PROPOSTO] | Apresentação como centro da experiência; controles sobrepostos e detalhes em painel lateral | Reduzir competição entre fotografia e interface |
| VIS-006 | [PROPOSTO] | Preservar enquadramento por padrão; preenchimento com recorte opcional | Evitar cortes involuntários, permitindo comparar imersão |
| VIS-007 | [PROPOSTO] | Suspender avanço automático enquanto controles ou painéis estiverem abertos | Dar tempo para navegar e avaliar legibilidade |
| VIS-008 | [PROPOSTO] | Explorar cenas de 6, 12 e 20 segundos; iniciar demonstração pausada com controles visíveis | Valores experimentais, não metas de desempenho ou defaults finais |
| VIS-009 | [PROPOSTO] | Mostrar título/contexto da coleção; informações de captura somente quando disponíveis | Explorar hierarquia sem inventar metadados |
| VIS-010 | [VALIDAR] | Reaproveitar componentes, tokens e modelos no frontend embarcado | Depende de WebView real, desempenho e contrato nativo |

As cores, fontes e comportamentos iniciais são escolhas de implementação para
comparação, não respostas implícitas às perguntas de produto abertas no PRD.
Ao aprovar uma alternativa, atualize o estado da linha pertinente e registre a
evidência do feedback. Não marque um gate técnico como concluído por aprovação estética.

## 3. Direções de identidade

### Cinema acolhedor

Grafite esverdeado, marfim e acento âmbar. Títulos editoriais em serifada e
controles em sem serifa. O tom é contemplativo e próximo, com pouco texto sobre
a fotografia. O acento indica foco, seleção e ação principal, sem tingir a mídia.

### Galeria neutra

Grafite neutro, branco suave e acento claro. Títulos e controles em sem serifa.
Mantém estrutura e fotografias para que a comparação avalie a linguagem visual,
sem introduzir diferenças funcionais como variável adicional.

### Marca e tipografia

Wordmark `memora.` em minúsculas, acompanhado de três traços que sugerem
fragmentos de memória. É um estudo vetorial provisório implementado em
[Icons.tsx](../apps/shared/presentation/src/Icons.tsx), não um logo final nem um pacote de marca.
A marca aparece no laboratório e nos controles; desaparece da apresentação limpa.

O protótipo usa fontes do sistema, sem requisições a fornecedores de fontes.
A aparência pode variar entre sistemas operacionais. Antes de consolidar a
identidade, escolher uma família com licença adequada, empacotar seus arquivos e
testar acentos, numerais e leitura à distância. Não há fonte final aprovada.

### Tokens e geometria

Os valores executáveis têm fonte única em
[styles.css](../apps/shared/presentation/src/styles.css), nos tokens `.app` e `.theme-gallery`:

| Token | Responsabilidade |
|---|---|
| `--bg` | Fundo do laboratório e dos estados |
| `--surface`, `--surface-strong` | Controles, cartões e painel |
| `--ink`, `--muted` | Texto principal e contexto secundário |
| `--accent`, `--on-accent` | Foco, seleção e ação principal |
| `--line` | Separação e limites de elementos |

O viewport de TV é 16:9 no desktop, com espaçamento proporcional à cena.
O laboratório se adapta a janelas menores; no celular admite altura mínima para
manter os controles utilizáveis. Essa adaptação não representa outro dispositivo-alvo.
Painéis têm cantos discretos, foco por contorno e seleção por cor mais símbolo.
As medidas são estudos do navegador; zonas seguras e escalas de leitura na TV
ainda exigem avaliação no hardware.

## 4. Arquitetura da experiência

| Superfície | Conteúdo e interação no protótipo |
|---|---|
| Apresentação | Cena atual, título/contexto opcional, acesso aos controles |
| Controles | Cena anterior, play/pausa, próxima, coleções, ajustes, fechar |
| Coleções | Capas, nome, contexto e ativação de cada coleção demonstrativa |
| Ajustes | Duração, preservar/preencher, informações e movimento |
| Laboratório externo | Troca de direção visual, seleção de cena, simulação de estado e reset |

As coleções ativas filtram a fila. Desativar todas conduz ao estado vazio;
reativar uma permite voltar à apresentação. A seleção explícita de um estudo
na bancada reativa sua coleção, retorna ao conteúdo e deixa a reprodução pausada
com controles visíveis para comparar o resultado.

O botão play inicia e fecha os controles; o botão pausa interrompe a reprodução
e também fecha os controles. Fechar o menu não muda a intenção de play/pausa.
O avanço automático fica suspenso com menu/painel aberto ou aba oculta.
Ao retomar, começa um novo intervalo completo para a cena corrente; não há
persistência de posição temporal. Sequência é ordenada e cíclica, sem shuffle nesta versão.

### Navegação e foco

- Setas representam o D-pad dentro do viewport. Com controles fechados,
  esquerda/direita trocam a cena e cima/baixo abrem o menu.
- Enter no viewport abre/fecha controles; sobre um botão, ativa sua ação.
- Espaço no viewport alterna play/pausa. Não captura espaço nos controles externos.
- Escape/Backspace fecha primeiro o painel e depois os controles; com a cena
  limpa, abre controles para permitir navegação. O navegador pode reservar Escape
  para sair do modo tela cheia.
- Abrir controles leva o foco ao play/pausa; abrir painel leva ao botão fechar.
  Fechar painel restaura o acionador quando ele ainda existe.
- Nos painéis, Tab circula entre os controles; setas em selects seguem o
  comportamento nativo do navegador. Fora deles, a navegação considera a posição
  dos elementos e não salta para o laboratório.
- Mouse é uma conveniência para feedback; os fluxos principais devem funcionar
  com teclado. Controle Fire TV e mapeamento nativo permanecem sem validação.

### Estados simulados

Conteúdo disponível, preparação, falha de conexão, seleção expirada e vazio.
Cada estado oferece contexto e ação de saída. CTAs de reconexão e nova seleção
apenas retornam ao mock; nenhum login ou QR Code funcional é apresentado.
Consulte [PRD §7](00-PRD.md#7-modelo-de-estados-e-ciclo-de-vida) antes de mapear
esses estudos à máquina de estados real: os nomes locais não são seu contrato.

## 5. Composição e movimento

| Estudo | Construção | Pergunta de avaliação |
|---|---|---|
| Individual | Uma imagem horizontal, preservada ou com preenchimento | A fotografia tem presença sem recorte? |
| Dupla de retratos | Duas imagens verticais em slots paralelos | Relação entre tamanho, respiro e leitura é confortável? |
| Mosaico | Principal à esquerda, apoio central e dois slots à direita | A hierarquia ajuda a olhar sem sobrecarregar? |

CSS Grid define os slots; `object-fit` controla o recorte sem distorção.
Layouts são curados e fixos, sem seleção algorítmica por proporção ou afinidade
temporal nesta etapa. Os modelos registram dimensões para permitir a evolução.

A troca monta uma nova cena com entrada por opacidade. Não há crossfade entre
duas cenas pré-carregadas nem garantia de ausência de quadro escuro. O zoom lento
é limitado à cena individual em modo preencher, preservando o enquadramento
integral no modo preservar. Com movimento desligado, a entrada não anima.
`prefers-reduced-motion` desativa animações e transições, sem impedir avanço das cenas.

Esta versão permite julgar direção e ritmo, mas não comprova FR-008 nem fluidez
4K. Pré-carregamento, transições entre cenas prontas e métricas pertencem à
validação posterior. Vídeos e Live Photos não estão simulados como reprodução real.

## 6. Base técnica e reaproveitamento

O projeto é um frontend estático React/TypeScript com Vite. As versões realmente
instaladas ficam em [package-lock.json](../apps/package-lock.json);
pré-requisitos e comandos ficam no README. Não há backend nem dependência de
servidor remoto para servir imagens ou fontes durante a experiência.

| Arquivo / camada | Responsabilidade | Possível reaproveitamento |
|---|---|---|
| `apps/shared/presentation/src/model.ts` | Tipos e preferências | Catálogo recebido por contrato |
| `apps/shared/presentation/src/demoCatalog.ts` | Catálogo demonstrativo | Provedor substituível, sem dependência da apresentação para fixtures |
| `apps/shared/presentation/src/usePresentation.ts` | Estado, reprodução e navegação | Fonte única nas duas entradas |
| `apps/shared/presentation/src/Presentation.tsx` | Composições, controles e painéis | Compartilhado por browser e TV |
| `apps/shared/presentation/src/Icons.tsx` e `styles.css` | Ícones, tokens, temas e estilos | Fonte única visual |
| `apps/shared/presentation/public/media` | Fotografias demonstrativas | Fonte única dos arquivos, servida por ambos os builds |
| `apps/prototype/src/App.tsx` | Bancada e simuladores | Exclusivo do browser |
| `apps/tv/src/main.tsx` | Entrada da TV | Apresentação sem bancada |

O contrato inicial de cena contém ID, título, subtítulo, layout, fotos e ID da
coleção. Foto contém ID, referência local, texto alternativo e dimensões.
Preferências contêm tema, recorte, duração, metadados, movimento e coleções ativas.
Esses tipos são contratos internos do mock, não uma bridge Android aprovada.

Preferências são salvas sob chave própria versionada no `localStorage`. A leitura
valida os valores conhecidos e a escrita trata armazenamento indisponível.
Não são persistidos credenciais, mídia pessoal nem histórico de uso. O reset
restaura apenas as preferências e o estado deste laboratório.

CSS nativo é suficiente para os três estudos. Tailwind, GSAP e bibliotecas de
galeria não foram instalados; as candidatas do
[PRD §8.2](00-PRD.md#82-motion--styling) permanecem propostas para o produto.
Evita-se antecipar dependências antes de demonstrar uma necessidade concreta.

A I2 separa bancada e apresentação, mantendo a aparência como exploração visual.
Antes de alterar o empacotamento ou a fronteira com o host, consulte o
[registro técnico](02-host-android-e-baseline.md#1-host-e-fronteira).
A validação no browser não comprova navegação física, lifecycle nem efeitos no
WebView real; esses critérios permanecem na etapa I3 do plano.

Fontes técnicas: [Vite — desenvolvimento](https://vite.dev/guide/),
[Vite — build e compatibilidade](https://vite.dev/guide/build.html),
[React — sincronização e limpeza de efeitos](https://react.dev/learn/synchronizing-with-effects).

## 7. Mídia e proveniência

Os arquivos estão em `apps/shared/presentation/public/media`. As quatro fotografias são públicas, demonstrativas e não pertencem ao acervo
pessoal. Não representam seleções reais do Google Fotos. Não foram atribuídos
data, localização de captura ou autor sem evidência.

| Arquivo local | Identificador da imagem no Unsplash |
|---|---|
| `coast.jpg` | [photo-1475924156734-496f6cac6ec1](https://images.unsplash.com/photo-1475924156734-496f6cac6ec1) |
| `mountain.jpg` | [photo-1500530855697-b586d89ba3ee](https://images.unsplash.com/photo-1500530855697-b586d89ba3ee) |
| `deer.jpg` | [photo-1472396961693-142e6e269027](https://images.unsplash.com/photo-1472396961693-142e6e269027) |
| `forest.jpg` | [photo-1441974231531-c6227db76b6e](https://images.unsplash.com/photo-1441974231531-c6227db76b6e) |

Obtidas em 2026-10-25 com largura de 1800 px e qualidade 85, para servir localmente.
Uso demonstrativo conforme a [licença Unsplash](https://unsplash.com/license),
consultada nesta data. Essa licença é distinta da licença do código. O arquivo
`mountain.jpg` retrata uma estrada entre rochas; seu nome é apenas identificador
local, não metadado geográfico.

## 8. Tarefa executável e critérios de revisão

Recorte local: construir uma bancada de conceito navegável no browser com duas
direções visuais, três composições, controles, coleções, ajustes e estados mock;
registrar decisões e referências. Não inclui integração, deploy ou fechamento
dos spikes. Rastreabilidade: FR-006..011, FR-013 e FR-016 como estudos de UX;
não constitui aceite funcional desses requisitos no produto final.

Critérios para revisão deste incremento:

- Abrir o protótipo local e comparar as duas direções com a mesma cena.
- Percorrer os controles e painéis com teclado e restaurar o foco ao voltar.
- Alternar enquadramento e informações; persistir ajustes após recarregar.
- Ativar/desativar coleções, incluindo o caso sem coleções ativas.
- Exercitar preparação, offline e expiração, com saída explícita da simulação.
- Conferir build TypeScript/Vite e ausência de erros do navegador no smoke.
- Revisar documentação e referências sem duplicar requisitos do PRD.

Para organizar a publicação deste incremento, consulte o
[fluxo orientado por issues](../AGENTS.md#desenvolvimento-orientado-por-issues).

## 9. Validação e próximo debate

Verificações locais executadas em 2026-10-25, com Node.js 22.17.0:

| Verificação executada | Resultado |
|---|---|
| `npm install` em `apps/prototype` | Dependências instaladas e lockfile gerado; auditoria inicial sem vulnerabilidades reportadas |
| `npm run build` | TypeScript e build de produção aprovados |
| `npm audit --omit=dev` | Nenhuma vulnerabilidade reportada nas dependências de produção |
| `npm run dev` e consulta HTTP local | Servidor em loopback, resposta 200 |
| `npm run preview` e consulta HTTP local | Build estático servido, resposta 200 |
| Smoke com Playwright/Chromium | Temas e persistência, ajustes e persistência, foco e Tab no painel, coleções vazias e recuperação, estados simulados, três layouts e teclado aprovados |
| Geometria e mídia no browser | Imagens locais carregadas e limitadas aos slots; viewport de 390 px sem overflow horizontal |
| Smoke do build estático | Avanço automático em 6 s, suspensão sob menu, movimento reduzido e carregamento de imagens aprovados; sem erros de execução observados |
| Inspeção visual | Capturas de desktop e painel em janela estreita revisadas; evidências locais conforme a [regra de capturas](../AGENTS.md#capturas-de-navegador-e-artefatos-playwright) |
| Links Markdown | Caminhos e âncoras locais de PRD, AGENTS, README e deste documento conferidos por script |
| `git diff --check` | Sem erros após revisão |

O smoke encontrou e corrigiu dois problemas: retorno de foco quando o acionador
era remontado e dimensionamento da imagem que podia provocar recorte no modo
preservar. Também foram mantidos nomes acessíveis nos botões cujos textos são
ocultados em janelas estreitas. Os fluxos foram reexecutados após as correções.

A primeira versão do protótipo foi aprovada pelo desenvolvedor em 2026-10-25
para integração ao repositório. A escolha entre as direções visuais e as demais
decisões finais de identidade permanecem abertas. Testes no browser
não comprovam contraste de todas as combinações, acessibilidade completa,
desempenho em 4K ou comportamento no WebView. Não houve acesso ao Fire Stick,
integração OAuth/Picker, reprodução de vídeo ou teste de hardware.

No próximo feedback, registrar: direção preferida; tamanho e posição de título;
quantidade de informação durante a cena; conforto de preservar versus preencher;
ritmo das cenas; entendimento dos menus e dos estados de recuperação.

Permanecem abertos: identidade final, família tipográfica empacotada, comportamento
de entrada/retomada do produto, relógio atual, data de captura breve ou permanente,
shuffle, vídeo/áudio, seleção/importação e desempenho no Fire Stick. Consulte as
[questões de produto do PRD](00-PRD.md#perguntas-de-produto-ainda-abertas-não-bloqueiam-redigir-este-prd)
antes de consolidar essas decisões.
