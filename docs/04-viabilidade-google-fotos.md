# Viabilidade Google Fotos — fontes e evidências

> Data da consulta: 2026-10-25 · Estado: G1 avaliada com impedimentos; ensaios Google não iniciados.
> Não é comprovação de integração, conformidade aprovada ou aceite do MVP.

## 1. Papel e fronteira

O [PRD](00-PRD.md#3-descobertas-e-restrições-verificadas) concentra definições,
requisitos e riscos. Este registro acrescenta fontes datadas, perguntas de ensaio
e evidências operacionais futuras. Sequência e progresso ficam no
[plano de viabilidade](plans/02-viabilidade-google-fotos.md#5-central-de-progresso),
aprovado pelo desenvolvedor. Consulte-o antes de iniciar qualquer experimento.
Não copiar aqui o tracker ou critérios completos das issues.

A [demonstração instalada](plans/01-primeira-versao-instalavel.md#5-central-de-progresso)
valida sua experiência local. O [host](02-host-android-e-baseline.md#1-host-e-fronteira)
não contém conector Google; suas permissões e allowlist atuais não autorizam
simplesmente carregar páginas OAuth ou mídia autenticada no WebView.

## 2. Consulta de fontes primárias

| Fonte consultada | Relação com o PRD / investigação necessária |
|---|---|
| [OAuth para TV — escopos permitidos](https://developers.google.com/identity/protocols/oauth2/limited-input-device#allowedscopes) | Confirma o bloqueador da §3.2. Não tratar login básico como concessão de acesso ao Picker. |
| [Políticas OAuth](https://developers.google.com/identity/protocols/oauth2/policies) | Exigem cliente compatível com cada plataforma. Avaliar onde autorização, callback, troca e custódia ocorrem; copiar um exemplo web não demonstra adequação ao APK. |
| [Fluxo para desktop](https://developers.google.com/identity/protocols/oauth2/native-app) | Referência de PKCE/redirect para sua plataforma; êxito no Mac é controle de bancada, não prova de suporte para Android/Fire TV. |
| [Configuração Photos](https://developers.google.com/photos/overview/configure-your-app) | Confirmar pré-requisitos da §3.1. O exemplo de cliente web é contextual, não escolha automática para o Memora. |
| [Sessões — REST](https://developers.google.com/photos/picker/reference/rest/v1/sessions) | Revalidar expiração, configuração da seleção e condições de polling da §3.1 em respostas reais. |
| [Ciclo de sessão](https://developers.google.com/photos/picker/guides/sessions) | Controlar criação e limpeza. Seu exemplo de polling apresenta host diferente do REST; para o ensaio, conferir endpoints na referência REST em vez de copiar exemplos sem verificação. |
| [Listagem — REST](https://developers.google.com/photos/picker/reference/rest/v1/mediaItems/list) | Paginação por token e pré-condição de seleção pronta. Uma página curta não prova término: observar a presença do próximo token. |
| [Obtenção de mídia](https://developers.google.com/photos/picker/guides/media-items) | Revalidar a distinção entre validade da URL e autorização da §3.1; recebimento de IDs não comprova bytes recuperáveis depois. |
| [Política Photos](https://developers.google.com/photos/support/api-policy) | Avaliar o enquadramento do Memora e as condições de uso/retenção descritas no risco R-013; funcionamento técnico não resolve essa avaliação. |
| [Quotas](https://developers.google.com/photos/overview/api-limits-quotas) | Conferir limites vigentes antes dos ensaios; não usar quotas publicadas como meta de carga nem inventar custo. |

As páginas são documentação pública consultada nesta data, não evidência do
comportamento da conta de teste. Reconsultar antes de decisões de implementação.

## 3. Desenho e condições de autorização

A [avaliação inicial G1](#51-g1--enquadramento-e-arquitetura-candidata) compara
caminhos Picker. A [retomada Ambient](#52-g1--tv-gallery-e-acesso-à-ambient-api)
corrige a cobertura. A [retomada Picker](#53-g1--retomada-do-caminho-picker)
registra o encaminhamento vigente. Nenhuma arquitetura está
aprovada para implantação.

Para cada candidato registrar tipo de cliente, componentes executados em cada
dispositivo, posse das credenciais/tokens, redirect, associação à TV, consentimento,
proteção contra replay/troca de aparelho e dependência de serviços Google no Fire OS.
Um serviço web verdadeiro, se necessário, exige decisão de arquitetura/custo;
não se torna aceitável apenas por substituir o tipo do cliente no console.

A avaliação de políticas deve descrever o uso concreto, confrontá-lo com os usos
permitidos e restrições, e apontar se há dúvida que exige esclarecimento oficial.
Não declarar conformidade apenas por uso pessoal ou comparação com outro app.

## 4. Protocolo de evidências

Cada ensaio deverá registrar, de forma sanitizada: fonte/commit e versão do cliente,
ambiente, pré-condições, passos executados, resultado esperado/observado,
contagens/tempos, erros classificados, limites e instrução de reprodução.
Distinguir teste real, mock e leitura documental. Dados brutos ficam privados
conforme AGENTS.md e PRD §10/12; nunca publicar tokens, IDs pessoais, URLs de
mídia, pickerUri, capturas de conta ou fotografias pessoais.

Para G4, manter matriz independente para validade OAuth, URL, sessão e catálogo
local: registrar horários UTC de obtenção/expiração, observações antes/depois,
se bytes vieram de rede ou cache e intervenção solicitada ao usuário. Reabrir o
app não prova sobrevivência à expiração. Renovar OAuth não é evidência suficiente
de renovação de sessão; excluir sessão ou revogar acesso são testes distintos.
Não alterar o relógio global do Fire TV para acelerar ensaios.

Para G5, distinguir tamanho do álbum de origem, quantidade selecionável, itens
recebidos e existência de associação a álbum no payload. Nova seleção do mesmo
item pode verificar identidade entre sessões, sem concluir autorização perpétua.
A decisão sobre coleções deve registrar o que o aplicativo sabe e o que é apenas
nomeado pelo usuário, sem simular sincronização com álbuns.

## 5. Registro de resultados

Nenhum ensaio operacional Google foi executado. O resultado documental G1 abaixo
não encerra SPK-01. Consultar a central do plano para estado consolidado.


### 5.1 G1 — Enquadramento e arquitetura candidata

Avaliação de 2026-10-25 para a [issue #42](https://github.com/RogerVFbr/memora/issues/42).
Planejamento integrado pelo [PR #48](https://github.com/RogerVFbr/memora/pull/48).
Método: releitura do PRD, host, README, fontes oficiais da §2 e fontes adicionais
abaixo; inspeção do código versionado e consultas ADB somente em leitura.
Não houve login, acesso a mídia, criação de cliente/projeto ou instalação.

Esta análise inicial trata dos caminhos Picker e não esgota as alternativas
Google Fotos. Para o resultado vigente após investigar o TV Gallery, consultar
as retomadas [Ambient](#52-g1--tv-gallery-e-acesso-à-ambient-api) e
[Picker](#53-g1--retomada-do-caminho-picker). As leituras de
hardware abaixo pertencem à primeira avaliação; não foram repetidas na retomada.

#### Enquadramento e dúvida impeditiva

O uso concreto é apresentar continuamente seleções pessoais na TV, com pares de
retratos, mosaicos e transições, sem editar, compartilhar ou substituir o acervo.
Essas composições acrescentam valor visual, mas não demonstram, por si só, que a
apresentação deixa de constituir uma galeria genérica. A
[política Photos, seção de usos apropriados](https://developers.google.com/photos/support/api-policy#appropriate-access-to-and-use-of-google-photos-apis)
exige funcionalidade adicional e proíbe substitutos/galerias genéricas. A lista de
usos permitidos não fornece aprovação explícita para esta moldura digital.
**Inferência da análise:** há ambiguidade material; não foi demonstrada permissão
nem proibição conclusiva do Memora. Uso pessoal e seleção consentida não resolvem
R-013. Não usar conta real enquanto essa dúvida persistir.

Ação do proprietário: solicitar esclarecimento oficial, pelos canais de suporte
indicados na documentação Photos, descrevendo o recorte acima, uso pessoal,
seleção temporária, ausência de sincronização/espelhamento e cache transitório.
Perguntar se esse uso é permitido e quais condições se aplicam à apresentação e
retenção após expiração. Registrar resposta datada e sanitizada nesta issue;
nenhum contato foi enviado nesta etapa. Ausência de resposta não é consentimento.
Se a resposta excluir o recorte, levar a evidência a G6 antes dos ensaios dependentes.

#### Comparação de caminhos

| Candidato / tipo de cliente | Componentes, redirect e custódia | Resultado da avaliação |
|---|---|---|
| TV / Limited Input | APK solicita códigos; celular autoriza no Google; TV recebe tokens por polling, sem callback próprio | Descartado para Picker pela lista oficial de escopos; login básico não concede o escopo necessário. |
| Android / Google Identity Services | Activity Kotlin e cliente Android associado ao pacote/certificado; resolução de consentimento pela API Android, sem callback HTTP próprio; tokens nativos | Guia depende de `play-services-auth`; serviço não encontrado no alvo. Não há prova de suporte ao Fire OS sem GMS. Navegador instalado não supre essa dependência. |
| Desktop / PKCE | Processo desktop abre navegador, recebe código em loopback e guarda tokens localmente | Válido somente como controle de bancada desktop; não adotar esse cliente no APK. Loopback Android foi descontinuado. |
| Web verdadeiro + pareamento | Navegador do celular → Google → callback HTTPS registrado no serviço; troca e custódia no servidor; APK pareado por canal autenticado | Candidato Picker da avaliação inicial, não aprovado: exige serviço real, revisão de associação à TV e decisão específica de custo/privacidade. Não equivale a embutir cliente web no APK. |

Fontes adicionais consultadas na mesma data:
[Android authorization](https://developer.android.com/identity/authorization),
[Desktop OAuth](https://developers.google.com/identity/protocols/oauth2/native-app),
[migração loopback](https://developers.google.com/identity/protocols/oauth2/resources/loopback-migration)
e [OAuth web server](https://developers.google.com/identity/protocols/oauth2/web-server).
As [políticas OAuth](https://developers.google.com/identity/protocols/oauth2/policies)
exigem cliente por plataforma e navegador seguro; PKCE isoladamente não torna um
cliente de outra plataforma apropriado. Não abrir OAuth no WebView do Memora.

#### Desenho condicional do candidato web

Este é um desenho para decisão, não uma arquitetura selecionada ou suporte
oficial comprovado ao pareamento específico do Memora:

1. APK Kotlin gera segredo de posse e inicia pareamento por HTTPS. Serviço cria
   transação curta e descartável; QR contém apenas referência de ativação, sem
   token Google nem segredo que permita ao observador resgatar a autorização.
2. Celular abre site do serviço. Consentimento prévio do Memora explica uso,
   retenção e vínculo com a TV. Usuário confere código nas duas telas e confirma
   no Memora pelo controle, evitando vincular uma TV por simples leitura do QR.
3. Serviço inicia OAuth com cliente **Web application**, `state` ligado à sessão
   do navegador/transação e callback HTTPS exato sob domínio controlado.
   Validar estado, sessão, expiração e uso único do código; proteger contra CSRF,
   replay e troca de aparelho. Avaliar PKCE adicional no fluxo escolhido, sem
   confundi-lo com autenticação do cliente confidencial.
4. Somente servidor troca código e guarda secret/tokens criptografados. APK
   recebe credencial própria de pareamento vinculada à prova de posse, revogável,
   por TLS; React não recebe credenciais. Cancelamento/timeout apagam a transação;
   polling tem prazo e limites. Separar autenticação da TV da seleção Picker.
5. Serviço cria/consulta sessão Picker com o mesmo cliente que recuperará seus
   recursos. Celular acessa Picker; TV obtém apenas status/dados permitidos pela
   fronteira nativa. A documentação Photos vincula recursos ao cliente original;
   não supor que um token Android poderá recuperar sessão criada por cliente web.

Para manter tokens no servidor, a obtenção autenticada de mídia exigiria proxy
transitório de bytes pelo serviço. Isso conflita com a diretriz atual de não
encaminhar fotos a servidores próprios (PRD NFR-009), além de gerar tráfego/custo.
Transferir token Google ao APK evitaria esse proxy, mas não está justificado pelo
fluxo web consultado e exige análise específica de plataforma/custódia; não está
aprovado como atalho. Essa escolha é **impeditiva**: não implementar nenhuma das
duas variantes sem decisão e justificativa compatíveis com as políticas e o PRD.

Serviço apenas temporário de onboarding não garante operação cotidiana: se tokens
e recuperação ficam nele, precisa estar disponível nas sessões de apresentação,
refresh e reseleção. Custos incluem hospedagem, domínio/TLS, armazenamento seguro,
logs sem dados pessoais, manutenção e eventual egress de mídia. Nenhum provedor,
preço ou gratuidade foi verificado/contratado; R$ 0/mês não foi demonstrado.
Proposta para discussão: primeiro esclarecer enquadramento; depois decidir entre
investigar serviço próprio e manter o gate inconclusivo. Não contratar/implantar
infraestrutura nem mudar NFR-009 nesta issue sem decisão expressa.

#### Inventário de pré-requisitos

| Pré-requisito | Situação comprovada / ação necessária |
|---|---|
| Baseline Memora | Host, pacote dev, pipeline e demonstração disponíveis; sem conector Google no código inspecionado. Não afrouxar allowlist do WebView para OAuth. |
| Dispositivo | `firestick-check` retornou `ok`, ADB autorizado e identidade esperada. Apenas leituras; diagnóstico bruto privado fora do Git. |
| Navegador externo | Consulta `cmd package query-activities --brief` para VIEW/BROWSABLE HTTPS encontrou handler `com.amazon.cloud9`; `resolve-activity` retorna seletor. Não abriu navegador nem testou cookies, login, D-pad, callback ou Custom Tabs. Disponibilidade registrada, compatibilidade OAuth ainda não comprovada. |
| Google Play Services | `pm path com.google.android.gms` sem caminho, saída 1. Não instalar GMS nem modificar outros aplicativos para contornar. |
| Projeto/API Google Cloud | Não inspecionado; proprietário deve identificar/criar projeto de teste e confirmar Photos Picker API habilitada. Não confundir com Google Picker ou Library API. Nenhum nome/ID precisa ser publicado. |
| Consentimento e conta | Não inspecionados; proprietário deve confirmar audiência, modo de teste, usuário de teste, escopo mínimo e aviso/política de privacidade. Credenciais e senhas ficam fora da conversa. Não presumir verificação/aprovação por funcionar em teste. |
| Cliente e callback | Não disponibilizados/verificados. Definir plataforma após resolver o caminho; se web, domínio HTTPS autorizado, callback exato e secret somente no serviço. Não criar Desktop para o APK. |
| Amostras consentidas | Não selecionadas nem inspecionadas. Proprietário confirma disponibilidade privada para G3/G5 conforme suas issues; não versionar mídia ou IDs. |
| Serviço, custódia e custo | Não existentes no recorte inspecionado nem aprovados. Exigem decisão específica e solução da fronteira de mídia acima. |

Para reproduzir as leituras: executar primeiro o diagnóstico real do README;
com o alvo privado já validado, consultar `cmd package resolve-activity --brief`
e `cmd package query-activities --brief` com ação `android.intent.action.VIEW`,
categoria `android.intent.category.BROWSABLE` e `https://example.com`, e
`pm path com.google.android.gms`, sempre via `adb -s <alvo> shell`. Nenhuma dessas
consultas inicia Activity; não usar `am start` em outro aplicativo nesta etapa.

#### Resultado inicial e condição de retomada

A investigação produziu mapa e inventário, mas não satisfez a passagem para G2:
R-013 continua sem esclarecimento e não há caminho aprovado de autorização e
obtenção de mídia. G1 permanece aberta/bloqueada; não é inviabilidade conclusiva.
Retomar #42 com resposta oficial sobre enquadramento e decisão específica do
proprietário sobre investigar infraestrutura/fronteira de dados/custo. Depois,
confirmar configurações externas em ambiente privado e revisar a passagem.
Sem resposta ou solução justificável, levar os impedimentos à decisão G6 em
retomada expressamente autorizada. Nenhuma etapa seguinte executada aqui.


### 5.2 G1 — TV Gallery e acesso à Ambient API

Retomada da [issue #42](https://github.com/RogerVFbr/memora/issues/42) em 2026-10-25,
a pedido do desenvolvedor, para aprofundar a referência já testada no Fire Stick.
O proprietário confirmou nesta retomada: **não possui acesso aprovado à Ambient
API**. Não foi inspecionado um projeto Cloud nem se presumiu inexistência dele.
Esta seção complementa a conclusão inicial da §5.1. O encaminhamento vigente
foi posteriormente definido na [retomada Picker](#53-g1--retomada-do-caminho-picker);
a candidatura abaixo é material histórico, não uma ação necessária agora.

#### Evidências públicas e grau de certeza

| Fonte consultada em 2026-10-25 | Evidência / limite |
|---|---|
| [TV Gallery — site do desenvolvedor](https://anytv.app/gallery/) | Descreve QR ou `google.com/device`, escolha de álbuns no Google Fotos e leitura direta de fotos pela TV. Não nomeia a API. |
| [TV Gallery — privacidade, §1.1/1.2/1.4/7](https://anytv.app/gallery/privacy/) | Declara intermediário OAuth próprio para login, refresh e revogação; credencial confidencial no servidor, códigos/tokens processados transitoriamente, tokens persistidos somente na TV e mídia direta do Google. Não é auditoria independente da implementação. |
| [Ambient — visão geral](https://developers.google.com/photos/ambient/guides/about) | Oferece integração de slideshow em TVs e molduras; usa autorização para dispositivos. |
| [Ambient — configuração](https://developers.google.com/photos/ambient/guides/configure-your-app) | Cliente TV/Limited Input; exemplo com `photosambient.mediaitems`, `state` contendo `requestId` e `displayName`; fluxo com celular, sem callback web do Memora. |
| [Programa de parceiros — seção Ambient](https://developers.google.com/photos/partner-program/overview#ambient-api) | Aceitação no programa é pré-requisito de acesso à Ambient; para lançamento, há revisões OAuth e de integração. A disponibilidade geral de Picker/Library não se estende à Ambient. |
| [Ambient — fluxo](https://developers.google.com/photos/ambient/guides/get-started) e [devices REST](https://developers.google.com/photos/ambient/reference/rest/v1/devices) | Registro de dispositivo, `settingsUri`, fontes selecionadas e polling. Não transportar para esse modelo a semântica de sessão Picker. |
| [Ambient — listagem REST](https://developers.google.com/photos/ambient/reference/rest/v1/mediaItems/list) | Fontes específicas ou seleção curada; limite publicado de 240 requisições/dispositivo/dia e páginas de até 100 itens. Ausência de próximo token não assegura esgotamento do acervo. |
| [Diretrizes UX Ambient](https://developers.google.com/photos/support/ambient-ux-guidelines) | Diretrizes beta próprias para conexão, identificação da conta, desconexão, seleção e apresentação. Não é autorização individual para o Memora. |

**Inferência:** registro de dispositivo, seleção de fontes no Google Fotos e fluxo
OAuth tornam Ambient uma explicação fortemente compatível com o TV Gallery.
Não encontramos declaração nominal da API pelo autor, código ou captura de rede
que confirme endpoints/escopos. Não afirmar que sua integração é Picker nem que
sua aprovação se transfere ao Memora. Não acessamos o APK, dados ou tráfego dele.

#### Correção da avaliação e arquitetura candidata

A afirmação de que o fluxo TV não suporta **Picker** continua válida. Ela não
impede o fluxo **Ambient**, cujo guia específico documenta outro escopo. A ausência
de GMS ou de navegador na TV não é, por si, impedimento a esse fluxo REST: a
interação Google acontece no celular. Não precisamos trocar clientes por Desktop.

O candidato passa a ser, condicionado ao acesso e à decisão de infraestrutura:

| Componente | Responsabilidade e custódia propostas |
|---|---|
| Kotlin no Fire TV | Iniciar/consultar fluxo de dispositivo, controlar expiração/cancelamento, guardar tokens criptografados com chave local; criar registro Ambient e obter mídia. React recebe somente estado e referências locais seguras. |
| Celular / páginas Google | Autenticar e consentir; configurar fontes do dispositivo pelo fluxo oficial. QR não transporta tokens Google. Retorno ao usuário no fluxo Google; sem redirect HTTPS de código OAuth para um site Memora. |
| Intermediário OAuth Memora, se necessário | Encaminhar somente operações OAuth permitidas para endpoints fixos e proteger a credencial que exigir custódia confidencial; não armazenar tokens/códigos nem encaminhar fotos. Implementação sujeita às condições do cliente aprovado; a arquitetura do terceiro não prova aceitação automática pelo Google. |
| Google Photos Ambient | Registrar dispositivo/fontes, servir listagem e bytes à camada nativa da TV. Sem servidor próprio no caminho de mídia. |

O intermediário precisa de HTTPS, validação estrita de payload/operação/cliente,
limites de uso, timeouts e exclusão de corpos, códigos e tokens dos logs; não pode
ser um proxy aberto. Vinculação de autorização e dispositivo deve respeitar o
fluxo aprovado e ser testada contra troca de transação, repetição e conta errada.
A necessidade exata de secret/intermediário deve ser confirmada nas condições do
cliente e onboarding, antes de escrever código ou expor um endpoint.

Esse desenho não exige mudar NFR-009, pois fotos não passam pelo intermediário.
Ainda depende dele nos eventos de autorização/refresh/revogação; não é promessa
de operação independente de servidor nem de acesso perpétuo. Não selecionamos
hospedagem, preço ou retenção de logs operacionais. R$ 0/mês e estabilidade não
foram demonstrados. Custos/implantação serão apresentados para decisão específica
após esclarecer elegibilidade, sem contratação ou deploy nesta investigação.

A Ambient documenta expressamente a categoria de slideshow do produto. Portanto,
não tratar a dúvida de galeria genérica do caminho Picker como uma proibição
conclusiva dessa alternativa. Políticas e revisão do recorte ainda se aplicam;
para esta rota, o primeiro impedimento comprovado é acesso ao programa.

#### Ação externa preparada

O [formulário oficial de interesse](https://services.google.com/fb/forms/photospartnerprogram/),
ligado pelo programa, foi lido, sem preenchimento ou envio. Solicita identificação,
contato, empresa/site, tamanho/localização da equipe, linguagem, caso de uso e
previsão de dispositivos; há opção de acesso para molduras/screensavers.
O texto introdutório ainda menciona Library/quota; usar a regra específica Ambient
do programa para interpretar a elegibilidade, sem presumir liberação automática.
A página não esclarece aceitação de desenvolvedor individual com uso pessoal,
nem garante prazo, aprovação ou isenção. Não inventar empresa/site comercial.

Rascunho do campo de caso de uso, para o proprietário revisar e usar na candidatura:

> Memora is a personal, non-commercial Android application for one Amazon Fire TV
> Stick 4K Max. It aims to display user-selected Google Photos sources as a
> remote-controlled slideshow with portrait pairs, mosaics and transitions.
> A local demonstration works; Google Photos integration is not implemented.
> We would like to request Ambient API access and confirm whether an individual
> developer with this personal-use scope is eligible. The proposed design keeps
> photos flowing directly from Google to the TV, uses transient local caching,
> and keeps tokens out of the presentation layer. If required, a minimal OAuth
> intermediary would protect confidential credentials without retaining tokens
> or proxying media. Please clarify onboarding, client credential requirements,
> permitted caching, and the review process for this scope.

Complementos conhecidos: integração nativa Kotlin/HTTP, interface React/TypeScript;
alvo inicial de um dispositivo próprio. Nome, contato, site e classificação do
proponente devem ser fornecidos pelo proprietário conforme a realidade.
Nenhum dado de contato ou identificação pessoal foi incluído no rascunho público.
Nenhum envio, contato com Google/Klebo ou aceitação de termos foi realizado.

#### Resultado da investigação Ambient

G1 continua sem passagem para G2. Primeiro, o proprietário deve solicitar acesso
ou esclarecimento de elegibilidade pelo canal oficial acima e trazer resposta
sanitizada; não enviar credenciais. Se já houver projeto, verificar privadamente
se foi autorizado para Ambient: simplesmente habilitar Picker não atende.
Após acesso confirmado, decidir cliente/custódia/intermediário e seu custo;
revisar explicitamente o recorte das issues dependentes, hoje centradas em Picker,
antes de executá-las por Ambient. O limite de 2.000 e sessões Picker não pode ser
copiado como aceite Ambient. Nenhuma dessas mudanças de escopo foi aplicada aqui.

Se o acesso for recusado ou permanecer indisponível, registrar o resultado e
levar a decisão ao desenvolvedor, sem chamar isso de falha técnica do Fire TV.
Não executar G2, implantar serviço ou tentar endpoints privados para contornar.


### 5.3 G1 — Retomada do caminho Picker

Em 2026-10-25, o desenvolvedor reafirmou o [caminho do PRD §3.1](00-PRD.md#31-google-photos-picker-api).
Esta retomada da [issue #42](https://github.com/RogerVFbr/memora/issues/42)
substitui o encaminhamento de candidatura Ambient da §5.2. Não é necessário
aguardar ingresso no programa de parceiros para investigar Picker; não houve
mudança do recorte das issues G2–G6 para Ambient. A configuração de projeto/API e
consentimento ainda não foi feita, conforme resposta do proprietário nesta retomada.

#### Preparação do projeto de teste

O proprietário pode preparar estes elementos sem escolher antecipadamente o
cliente OAuth ou contratar infraestrutura:

1. No Google Cloud Console, criar/selecionar um projeto de teste do Memora sob sua
   conta. Não publicar ID do projeto, dados de conta ou arquivos de credenciais.
2. Habilitar **Google Photos Picker API**. Não é Google Picker, Library ou Ambient.
3. Configurar Google Auth Platform com identificação real do Memora, e-mails de
   suporte/contato e audiência adequada à conta. Para conta pessoal, configurar
   audiência externa em teste e adicionar a própria conta como usuário de teste.
4. Em acesso a dados, cadastrar somente o escopo Picker definido no PRD §3.1.
   Não acrescentar escopos Library ou Drive para tentar contornar seus limites.
5. Informar apenas que API, consentimento e usuário de teste estão preparados.
   O tipo de cliente será decidido conforme o candidato OAuth verificado abaixo;
   não criar Desktop para o APK nem cliente web com secret embarcado.

Fonte: [configuração Photos](https://developers.google.com/photos/overview/configure-your-app).
O exemplo web dessa página não escolhe a plataforma do Memora. Modo de teste não
é aprovação de conformidade e pode afetar validade OAuth; medir no gate pertinente.
Não foi acessado ou alterado o Console nesta entrega.

#### Próxima investigação OAuth

Além do candidato web da §5.1, há uma alternativa Android ainda não avaliada
operacionalmente: cliente **Android** com navegador externo e retorno ao próprio
APK, PKCE e `state`. Evitaria GMS, backend e custódia de secret confidencial **se**
o Google e o navegador do Fire TV aceitarem esse fluxo para o cliente real.
É hipótese de ensaio, não suporte confirmado.

O [anúncio oficial de 2023](https://developers.googleblog.com/improving-user-safety-in-oauth-flows-through-new-oauth-custom-uri-scheme-restrictions/)
descreve bloqueio por padrão e uma opção avançada para habilitar URI própria
quando a alternativa recomendada não atende. Porém, o
[guia OAuth atual](https://developers.google.com/identity/protocols/oauth2/native-app)
informa que esse retorno não é suportado no Android, e a ajuda antiga
[Setting up OAuth](https://support.google.com/googleapi/answer/6158849?hl=en)
se declara desatualizada. A nova ajuda
[Manage OAuth Clients](https://support.google.com/cloud/answer/15549257?hl=en)
descreve registro Android por pacote e certificado sem esclarecer a exceção.
Não assumir que a opção histórica ainda existe ou resolve o Fire TV.

Ação ao preparar o cliente: conferir privadamente as opções efetivas do Console e
esclarecer a divergência de suporte antes de usar credenciais/dados reais. Se o
retorno for justificável, o desenho deve manter verifier/tokens em Kotlin, validar
estado/transação/expiração, usar callback restrito, rejeitar replay e expor zero
credenciais ao React. O retorno precisa ocorrer no aparelho que iniciou o fluxo:
ler no celular um QR OAuth com callback Android não devolve automaticamente o
código à TV. A prova inicial desse candidato seria no navegador externo da própria
TV, com impacto de usabilidade a medir; não pressupõe onboarding por celular.
Se for descartado, retomar desenho de pareamento/serviço web, apresentando
arquitetura, fronteira de dados e custo para decisão, sem implantação automática.

A avaliação de enquadramento da §5.1 continua aberta; a preferência por Picker não
é evidência de conformidade nem proibição conclusiva. A investigação de OAuth e
a preparação Cloud podem avançar sem dados de mídia enquanto esse ponto é tratado.
Não adicionar uma dependência de aprovação Ambient para resolver essa dúvida.

#### Resultado e próximo passo

G1 mantém aceite incompleto. Há trabalho definido para retomar: preparar
projeto/API/consentimento e verificar o candidato Android com retorno próprio,
resolvendo sua documentação contraditória. Só depois escolher cliente e autorizar
a prova operacional de G2 conforme o gate existente. Nenhum prazo/frequência de
reseleção foi aceito; sessões e expiração continuam sendo ensaios G3/G4.
O registro de direção não comprova OAuth no Fire TV,
não cria código/infraestrutura e não encerra #42.


#### Preparação Cloud executada

Ainda em 2026-10-25, após autenticação local feita pelo proprietário, Google Cloud
CLI 590.0.1 instalada via Homebrew foi usada para criar um projeto exclusivo de
teste, com nome visível **Memora Testes**, e habilitar Photos Picker API.
`projects describe` retornou `ACTIVE`; `services list --enabled`, filtrado para
`photospicker.googleapis.com`, confirmou o serviço habilitado. A configuração
local da CLI chama-se `memora`; identificadores de projeto/conta e credenciais
não entram no registro versionado. Não houve vinculação de faturamento ou deploy.

O login da CLI autoriza administrar Cloud; não é a autorização Picker do usuário
no aplicativo. Consentimento, usuário de teste, declaração do escopo e cliente
OAuth ainda não foram configurados/verificados. Próxima ação: concluir Branding,
Audience (externo em teste) e Data Access conforme o roteiro acima, antes de criar
o cliente. Não há fluxo suportado da CLI confirmado nesta etapa para automatizar
essa configuração de Google Auth Platform; não usar comandos de IAP como substituto.
Sem sessão Picker criada, mídia acessada ou teste no Fire Stick.


### 5.4 G1 — Candidato Android preparado para SPK-01

Em 2026-10-25, proprietário concluiu o consentimento no Console: escopo Picker
salvo, audiência External/Testing e um usuário de teste cadastrado, conforme
capturas apresentadas nesta sessão. Cliente Android criado para a variante dev,
com pacote e SHA-1 conferidos contra o APK local por `aapt dump badging` e
`apksigner verify --print-certs`. Advanced settings exibiu **Enable custom URI
scheme** e proprietário criou o cliente com essa opção marcada. Essa observação
resolve a dúvida sobre existência da opção neste projeto, não demonstra OAuth.

Exportação JSON validada localmente: bloco `installed`, client ID com formato
Google, projeto correspondente à configuração CLI, endpoints Google; sem client
secret. Arquivo ignorado em `.local/google/oauth-android-dev.json`, com permissão
600. Pacote, certificado e opção de URI não constam no JSON; evidência desses
campos é a tela do Console, conferida com APK, não a exportação. Não versionar ID,
JSON, e-mail, captura de conta ou token.

Desenho do ensaio: cliente Android público; navegador externo na própria TV;
retorno por esquema exclusivo derivado do client ID e caminho `/oauth2redirect`;
PKCE S256, `state` aleatório, validade curta e transação consumida uma única vez.
Verifier, código e tokens somente em Kotlin, em memória durante o ensaio; perda
de processo exige recomeçar. Nenhum secret, backend, JS ou QR transportando tokens.
Cancelar, expirar ou receber callback inesperado deve terminar sem requisição de
token. Uma chamada mínima de criar/remover sessão vazia verifica o escopo, sem
abrir Picker nem selecionar mídia. URL e ID da sessão não entram em logs.

A exceção do anúncio Google vinculada na §5.3 e a opção real do Console justificam
um experimento delimitado, sem declarar suporte operacional. Fricção de login na
TV e retorno via navegador são riscos do ensaio. Sem infraestrutura contratada,
o candidato não cria custo de hospedagem; nenhuma garantia de custo de outros
caminhos foi aprovada. Renovação/persistência e acervo ficam nos gates seguintes.

Avaliação de uso para esta passagem: o ensaio de autorização não implementa uma
galeria nem obtém fotos; a restrição de produto da §5.1 não impede preparar esse
ensaio isolado. A conformidade da apresentação e retenção continua a esclarecer
antes de obter/usar mídia em G3. Não usar esta conclusão de desenho como aprovação
Google do slideshow ou como autorização de acesso permanente.

G1 atende desenho/inventário e permite preparar SPK-01 nesse limite. G2 só recebe
aceite após autorização efetiva e operação mínima no Fire TV, testes negativos e
verificação de privacidade. Não declarar positivo pelo JSON ou pelo build.


## 6. Ensaio OAuth nativo de desenvolvimento

Implementação de SPK-01 / [#43](https://github.com/RogerVFbr/memora/issues/43),
conforme desenho da [G1](#54-g1--candidato-android-preparado-para-spk-01).
Comandos executáveis têm fonte no [README](../README.md#ensaio-oauth-picker-de-desenvolvimento).

`OAuthSpikeActivity` e `OAuthTransaction` existem somente em `src/debug`. A tela
nativa é aberta explicitamente por ADB, sem trocar o launcher demonstrativo.
Gradle lê a configuração privada e deriva esquema de retorno do client ID;
incorpora somente o ID público no debug. Exportação confidencial é rejeitada;
sem arquivo local, a tela informa falta de configuração. A Activity fixa o
navegador Amazon verificado no alvo para evitar handlers de players genéricos.
Não há ponte com React ou relaxamento da política de rede do WebView.

PKCE S256 e `state` aleatórios por transação; callback exige esquema/caminho exatos,
sem authority/fragmento ou parâmetros duplicados, estado correspondente e uso único.
Tentativa expira em cinco minutos (limite do ensaio, não validade Google);
cancelamento e perda de processo não restauram verifier. A UI usa FLAG_SECURE e
não mostra códigos/tokens. HTTP nativo usa HTTPS fixo, sem redirects, timeouts de
15 segundos e respostas limitadas; não há polling ou retry automático. Tokens
existem só na memória da tarefa, sem refresh/persistência nesta prova.

Após código aceito, troca token e cria/remove sessão vazia automaticamente, sem
abrir Picker nem ler itens. O sucesso requer ambas as respostas de sessão; se
limpeza falhar ou rede interromper após criação, permanece pendência: não chamar
essa tentativa de aceite completo. Estado do ensaio e mensagens são locais e não
persistentes. Cancelamento invalida resultado; requisição já em voo pode terminar
até o timeout e a limpeza da sessão criada é trabalho necessário. O aplicativo
não registra corpos/URLs/códigos em logs; navegador e logs do sistema Android
exigem cuidado e não são exportados automaticamente.

Verificações locais executadas: build debug, sete testes (cinco de OAuth e dois
existentes de origem local), lint debug e geração de manifesto/recursos release.
Inspeção dos manifestos confirmou Activity/rede só em debug, ausentes em release.
Testes cobrem PKCE, associação, replay, origem errada, duplicatas, cancelamento,
negativa e expiração. Não equivalem à prova de autorização do Google.

Hardware: diagnóstico desta retomada retornou parcial, somente foreground
indisponível em repouso; identidade/conectividade autorizadas. Instalação `-r`
concluiu e a abertura da tela foi confirmada pelo proprietário após despertar a
TV. Não houve limpeza de preferências, instalação de outro pacote ou mudança
global. O resultado operacional posterior está na [evidência positiva](#61-evidência-positiva-no-fire-tv) e as condições para a próxima etapa ficam no [aceite](#62-aceite-operacional-da-g2).


### 6.1 Evidência positiva no Fire TV

Em 2026-10-25, após o proprietário concluir o login Google com verificação pelo
celular, a primeira tentativa retornou ao Memora com mensagem de retorno rejeitado
(sem tentativa válida, expirado ou não associado). A demora é compatível com o
prazo de cinco minutos, mas a mensagem não distingue causas; expiração permanece
inferência, não diagnóstico confirmado.

Na nova tentativa, proprietário informou sucesso e o agente confirmou em leitura
no aparelho: **AUTORIZAÇÃO PICKER COMPROVADA — Sessão vazia criada e removida.
Nenhuma foto selecionada. Tokens não persistidos.** A confirmação foi obtida do
texto da interface do próprio Memora por UIAutomator; XML e diagnóstico brutos
ficam privados fora do Git. `firestick-check` dessa confirmação retornou `ok`.

Pelo fluxo implementado e revisado, essa mensagem só é emitida depois de retorno
associado à tentativa, troca do código com PKCE e respostas HTTP 2xx para criação
e remoção da sessão vazia. Assim, há evidência positiva de autorização Picker no
Fire TV com cliente Android e URI própria, sem backend. Não foi necessário
selecionar mídia nem usar cliente Desktop/TV limitado ou Ambient.

Isso não comprova persistência/refresh, expiração real, acesso recorrente, álbuns,
vídeos ou conformidade do slideshow. O [aceite posterior](#62-aceite-operacional-da-g2) acrescenta negativos controlados
e revisão de privacidade. A rejeição anterior não foi contada como ensaio
controlado de cancelamento/negação.


### 6.2 Aceite operacional da G2

Conclusão de SPK-01 / [#43](https://github.com/RogerVFbr/memora/issues/43) em
2026-10-25, sobre código integrado no [PR #54](https://github.com/RogerVFbr/memora/pull/54).
O diagnóstico `firestick-check` da rodada retornou `ok`. Nenhuma configuração
global foi alterada, nenhum pacote alheio instalado e nenhuma mídia selecionada.

| Caso no Fire TV | Método e resultado observado |
|---|---|
| Autorização positiva | Prova original da §6.1 e nova tentativa após os negativos: retorno ao app, troca PKCE, sessão vazia criada/removida e mensagem de sucesso confirmada por leitura UI. |
| Negativa real Google | Nova tentativa, conta de teste já autenticada e botão **Deny** na página Google. Retorno nativo: **Consentimento negado. Nenhuma sessão criada.** Não é callback mock. |
| Cancelamento no app | Iniciar tentativa, voltar à Activity sem callback e selecionar **Cancelar tentativa**. UI confirmou cancelamento; callback sintético posterior rejeitado. |
| Repetição de retorno inválido | Reenvio do callback sintético após cancelamento rejeitado novamente. Não foi reutilizado nem publicado código OAuth real. |
| Estado errado em tentativa ativa | Após iniciar e voltar à Activity, URI do cliente com estado/código deliberadamente sintéticos rejeitada. Verifica associação local, sem pedir token Google. |
| Processo perdido | Iniciar tentativa, `am force-stop` somente no Memora Dev e retornar com callback sintético. UI: **Retorno sem tentativa ativa. Inicie novamente.** |
| Expiração e replay de retorno válido | Cobertos pelos testes unitários existentes. Não foram apresentados como ensaios reais de expiração no hardware; rejeição inicial da §6.1 não prova sua causa. |

Roteiros ADB/UIAutomator de bancada foram locais/temporários; injeções sintéticas
não foram descritas como falhas reais Google. Capturas de páginas do navegador e
XML brutos ficaram privados, sem anexos; capturas descartáveis removidas após uso.
As mensagens do app confirmaram término dos negativos; revisão do código confirmou
nenhum loop de polling/retry e nenhuma chamada de token nesses ramos.

#### Revisão de privacidade e alcance

Revisados Activity, transação, configuração Gradle e manifestos: nenhuma chamada
de logging, gravação de token em disco, ponte JS ou QR OAuth. Verifier fica nativo;
callbacks consumidos/expirados são rejeitados; HTTP não segue redirects, tem
endpoints fixos e limites de tempo/resposta. JSON/IDs locais não estão no Git;
client ID público existe no debug privado e está ausente nos recursos release.
Manifesto release não contém a Activity ou INTERNET; artefatos do cliente local
não foram publicados. Não afirmamos proteção contra dispositivo comprometido.

Logcat do PID atual foi lido privadamente após nova autorização positiva, sem
marcadores de access/refresh token ou Bearer com valor. A tentativa de filtrar por
UID não foi suportada pelo aparelho e não foi contada como verificação. Amostragem
de logs não é prova universal de ausência; a revisão de código complementa-a.

Na leitura dos logs de ActivityTaskManager filtrados ao ensaio, o sistema registrou
URI de callback com código e estado temporários. Não havia token/verifier nesses
registros. O app não controla esse log. O achado foi informado ao desenvolvedor,
que decidiu ajustar o critério da issue para logs controlados pelo Memora; a
[decisão e seu alcance ficam no PRD §10](00-PRD.md#10-segurança-e-privacidade).
Não limpamos logs globais nem tentamos ocultar o resultado. Códigos/verifier/tokens,
identificadores e linhas brutas não foram publicados.

#### Resultado consolidado

G2 aceita no recorte de autorização: cliente Android/URI própria funciona no Fire
TV sem backend e permite operação mínima Picker; casos negativos terminam com
rejeição/cancelamento e sem polling infinito. Fonte integrada, build/sete testes/
lint e isolamento de release já registrados na §6; esta rodada não mudou código,
não exigiu novo build e acrescenta revisão documental/links e `git diff --check`.
Sem serviço contratado, custo de hospedagem não é exigido por este candidato;
não é medição de custos de uma versão futura.

Próxima etapa técnica é G3 / #44. Antes de obter mídia real, permanecem as
condições de amostra consentida e esclarecimento de enquadramento/retenção R-013
já registradas no PRD/G1; esta conclusão de OAuth não as remove. Nenhuma seleção,
G3, refresh/persistência ou validação de acesso recorrente foi iniciada nesta rodada.


## 7. Seleção e recuperação diagnóstica G3

Retomada de [#44](https://github.com/RogerVFbr/memora/issues/44) em 2026-10-25,
com G2 integrada/aceita. O desenvolvedor confirmou dispor e autorizar 10 fotos e
2 vídeos para recuperar bytes/metadados privadamente no Fire Stick, com escolha
pelo próprio Picker. Estendeu explicitamente o risco residual OAuth aos ensaios
G3–G5; alcance no [PRD §10](00-PRD.md#10-segurança-e-privacidade).

### Recorte e retenção antes de operar

Ensaio de recebimento/exportação temporária iniciado pelo proprietário, sem
exibição de galeria/slideshow. A [política Photos](https://developers.google.com/photos/support/api-policy)
contempla armazenamento/exportação e o [guia Picker](https://developers.google.com/photos/picker/guides/media-items)
documenta a obtenção dos itens selecionados. Isso esclarece a passagem para esse
recorte técnico; R-013 continua aberto para a apresentação final e sua retenção.
Não é aprovação nominal Google do Memora ou dispensa de políticas.

Tokens, sessão/expiração, IDs/URLs e metadados mínimos para G4 serão guardados
somente em arquivo privado cifrado pelo Android Keystore, excluído de backup.
Nenhum byte de foto/vídeo será retido: leitura limitada em streaming e descarte.
Preservar a sessão positiva até o ensaio real de expiração de G4; limpeza explícita
ao concluir a investigação ou a pedido do proprietário. Se o ensaio for abandonado,
remover sessão/dados privados; não transformar esse registro em catálogo permanente.
Evidências públicas limitam-se a contagens, status, tempos/durações sem IDs/URLs.

QR nativo contém somente o `pickerUri` necessário à seleção, nunca token OAuth;
fica restrito à tela de diagnóstico protegida e não é publicado em capturas/chat.
O proprietário usa a mesma conta de teste no celular e seleciona a amostra.
A biblioteca ZXing Core 3.5.3 é dependência apenas debug para gerar QR localmente,
sem serviço externo; fontes na [biblioteca](https://github.com/zxing/zxing) e
[release fixada](https://github.com/zxing/zxing/releases/tag/zxing-3.5.3).
Paginação real com pageSize5; fotos com resolução delimitada; vídeos READY só têm
acesso parcial aos primeiros bytes, explicitamente separado de download completo
ou playback. Resultados positivos/negativos serão acrescentados após execução.


### 7.1 Preparação e validação parcial

Código nativo debug implementado com separação SPK-01/SPK-02, QR local, polling
somente em foreground e prazo original não renovável. Core HTTP usa endpoints
fixos, sem redirects de API; tratamento restrito de redirects de mídia está no
[registro da primeira amostra](#72-primeira-amostra-e-ajustes-de-recuperação).
Foto recebe verificação de tipo/dimensões e teto de 8 MiB; vídeo READY recebe
leitura parcial de até 64 KiB. Respostas e catálogo têm limites, sem retry infinito.
`ProbeStore` usa AES-256-GCM/AAD, chave Android Keystore e escrita atômica; backup
Android permanece desabilitado. URLs iniciais, OAuth/expiry e histórico de sessão
são preservados separadamente para G4.

Revisão por subagentes e agente principal corrigiu duas classes de risco antes
do uso de mídia: histórico original de expiração/URLs não é sobrescrito e
cancelamento durante criação aguarda o resultado limitado em vez de interromper
a leitura e afirmar limpeza sem ID. Resultado remoto incerto não é anunciado
como removido; falha de rede/limpeza preserva evidência. Perda simultânea de
resposta e armazenamento impede garantia de rastreio remoto: limite explícito do
ensaio, sem promessa de limpeza quando não confirmada.

Build debug, 16 testes e lint aprovados. Testes cobrem OAuth/origem local existentes,
polling/deadline/background, paginação/ciclos/duplicatas, origem de mídia e limites
de leitura. Não são ensaios reais de HTTP 401/403/429/5xx. Manifesto/recursos release
gerados e dependência ZXing restrita às configurações debug, sem cliente ou
Activity de ensaio em release.

No Fire TV, `firestick-check` retornou `ok`, instalação `-r` e abertura aprovadas.
Sessão real incompleta foi criada sem selecionar itens; arquivo privado cifrado
confirmado por envelope, sem marcadores de token/URI em texto claro. Cancelamento
pelo app confirmou DELETE e remoção do arquivo local. Não foi feito acesso a mídia
nessa negativa. Outra sessão foi aberta para a amostra e QR confirmado pela UI;
seleção pessoal ainda depende do proprietário. Não dar aceite a G3 por esse código.


### 7.2 Primeira amostra e ajustes de recuperação

A primeira seleção retornou 11 itens: 9 fotos e 2 vídeos, em 3 páginas reais,
sem duplicatas e sem ausência dos metadados técnicos mínimos observados. As
9 fotos foram recuperadas e identificadas; ambos os vídeos eram READY, mas
retornaram HTTP 302 bloqueado pela política inicial. Não recebeu aceite por faltar
uma foto e não haver bytes dos vídeos comprovados naquele momento.

Corrigida somente a recuperação de mídia: até três redirects manuais, HTTPS
Googleusercontent validado, sem credenciais de URL/porta/fragmento; URL assinada
pode conter query. Bearer só vai no pedido original, nunca no destino do redirect.
Loops e hosts desconhecidos continuam bloqueados. API JSON segue sem redirects.
Trilha/Location só no snapshot cifrado; mensagem de erro não inclui capability.
Também adicionados revalidação da mesma sessão e informações de horários/contagens
lidas do snapshot sem rede; restauração não inicia polling ou inspeção automática.
Cancelar revalidação preserva evidência; descarte é ação explícita separada.

Rebuild, 19 testes e lint aprovados após os ajustes. Reinstalação sem limpeza
restaurou a evidência por Keystore no aparelho. Na primeira revalidação, nove
fotos e o prefixo de um vídeo foram recuperados; o outro sofreu
SocketTimeoutException, sem HTTP de erro e sem bloqueio de host. Diagnóstico lido
privadamente do snapshot, sem repetir a rede apenas para descobrir a causa.
Na tentativa controlada seguinte, nove fotos e os dois prefixos de vídeo foram
recuperados (1.653.896 bytes no total), sem falhas HTTP/redirect ou rede. Não houve
retry automático. A seleção seguiu incompleta por conter nove fotos, não dez.
Cancelar o resultado preservou o arquivo privado; a ação explícita Limpar
confirmou DELETE e remoção local antes da nova seleção.

As respostas reais dessa sessão indicaram expireTime sete dias após criação;
isso não define duração fixa universal. G4 precisa observar a fronteira real em
outra janela, preservando a amostra válida que vier a ser aceita. Não substituir
esse ensaio por mock ou relógio alterado. A sessão da amostra incompleta foi
limpa após o diagnóstico dos redirects, antes da nova seleção.

### 7.3 Aceite operacional da G3

A nova seleção feita pelo proprietário foi verificada no Fire TV em 2026-10-25:
10 fotos recuperadas e dois vídeos READY com dois prefixos de até 64 KiB;
três páginas reais, zero duplicatas e zero metadados mínimos ausentes.
Foram lidos 1.382.616 bytes, sem falhas, itens não prontos ou hosts bloqueados.
Isso atende ao aceite diagnóstico da [issue #44](https://github.com/RogerVFbr/memora/issues/44),
entregue pelo [PR #57](https://github.com/RogerVFbr/memora/pull/57).
Prefixos de vídeo não demonstram download integral, codec ou reprodução.

O snapshot privado cifrado foi preservado para G4; bytes de mídia não são retidos.
As âncoras observadas, em UTC, são: expiração OAuth em
2026-10-25T23:38:40.714Z; expireTime da sessão em
2026-11-01T22:38:41.914799Z; primeira URL obtida em
2026-10-25T22:41:23.836Z; última listagem em 2026-10-25T22:41:24.905Z.
Esses horários orientam a observação real de G4; não comprovam expiração nem
prazo universal. Preservar sessão e URLs iniciais até esse ensaio.

Permanecem os limites de retenção e política do recorte acima, R-013 para o
produto final e o risco residual de logs do Fire OS aceito no PRD §10 para
G3–G5. Não há aprovação nominal do Google nem validação de slideshow neste aceite.

## 8. Expiração e reabertura G4

G4 / [#45](https://github.com/RogerVFbr/memora/issues/45) iniciada após aceite e
integração de G3 pelo PR #57. Preservar a amostra e as âncoras da
[§7.3](#73-aceite-operacional-da-g3); não criar outra seleção, excluir sessão,
revogar autorização ou limpar dados antes das observações naturais.

A matriz separa consulta da sessão, acesso direto às URLs iniciais sem relistagem,
relistagem da mesma sessão e acesso às URLs retornadas. Renovação OAuth será uma
intervenção distinta, preservando as âncoras anteriores; não demonstra refresh
automático nem autorização permanente. Reiniciar apenas o processo do Memora
deve restaurar evidência sem executar rede automaticamente.

Fontes revalidadas em 2026-10-25: o [guia de mídia Picker](https://developers.google.com/photos/picker/guides/media-items)
exige Bearer válido e documenta URLs de 60 minutos;
[sessions.get](https://developers.google.com/photos/picker/reference/rest/v1/sessions/get)
distingue acesso expirado (PERMISSION_DENIED) de sessão ausente/não pertencente
à conta (NOT_FOUND). Um erro isolado não identifica a causa sem controlar OAuth,
idade da URL e expireTime. Não alterar relógio nem usar mock como observação.

### 8.1 Preparação e pendências

Reabertura real com processo novo executada ainda dentro das validades: o app
restaurou o resumo positivo G3 sem consulta ou download implícitos. O hash do
arquivo cifrado permaneceu idêntico antes/depois da abertura; combinado com a
revisão do caminho de restauração, evidencia que a abertura não renovou as
âncoras. Ainda não é prova de acesso à rede após expiração.

A instrumentação do [PR #58](https://github.com/RogerVFbr/memora/pull/58) separa
quatro operações explícitas, com intenção persistida
antes de cada requisição e histórico privado de respostas/falhas. Renovação OAuth
preserva a credencial original, a primeira URL e a sessão. Credencial nova começa
com associação não comprovada; sucesso na sessão confirma acesso, mas 403/404
isolado não demonstra que a conta selecionada é a mesma. Não há refresh automático.

Medições no alvo, antes das expirações: consulta de sessão HTTP 200; 12/12 URLs
iniciais acessíveis sem relistagem, com idades entre 460 e 471 segundos, 12 respostas
HTTP 200 e zero falhas. Relistagem separada percorreu três páginas e recuperou
os bytes delimitados dos 12 itens: 15 respostas HTTP 200, zero falhas.
Renovação explícita pelo navegador da TV não exigiu nova seleção; credencial 1
acessou a mesma sessão com HTTP 200. A original (credencial 0), preservada, também
respondeu HTTP 200 dentro de sua validade. A expiração da sessão e o horário da
primeira URL continuaram iguais aos de G3; a credencial 1 informa validade até
2026-10-25T23:50:26.762Z. São respostas de rede, com cache local desabilitado,
Date/Age/Cache-Control registrados privadamente; nenhum byte de mídia foi retido.

O teste de saída da Activity expôs continuação de leituras apesar de Future.cancel:
a interrupção da thread não bastou para encerrar o loop. Corrigido com sinal
explícito por operação, criado antes do envio ao worker e independente do flag
de interrupção que E/S pode limpar. A repetição real registrou ensaio interrompido
no histórico; o snapshot estabilizou e o resultado foi restaurado em processo novo.
Uma leitura já em voo pode terminar dentro dos limites antes de observar o sinal;
não se promete corte instantâneo do socket. Dois testes adicionais cobrem sinal
anterior ao início e interrupção limpa por E/S.

Build debug, 25 testes e lint aprovados após a correção. Revisão independente sem
impeditivos. Atualização preservou a evidência cifrada no aparelho. Nenhum desses
resultados constitui ensaio de expiração natural.

Os limites de leitura e a retenção privada continuam no
[recorte G3](#7-seleção-e-recuperação-diagnóstica-g3), sem reuso de bytes salvos.
Nenhuma expiração foi observada ainda. Retomar a partir das âncoras privadas e do
snapshot cifrado, sem substituí-los por nova seleção. A expiração natural da
sessão ocorrerá em outra data; G4 permanece sem aceite até reunir as evidências
exigidas pela issue.

### 8.2 Condição de retomada da matriz temporal

O código não executa em background nem agenda medições. É necessário retomar
este ensaio quando a fronteira natural tiver passado, preservando o snapshot:

1. Após a expiração OAuth original registrada na §7.3, consultar com **Testar OAuth
   original**; registrar resposta real, sem tratar falha de rede como expiração.
2. Após 60 minutos da primeira URL, usar OAuth comprovadamente válido e testar
   **URLs iniciais** antes de relistar. Se necessário, **Renovar OAuth sem nova
   seleção**, escolher a mesma conta e consultar a sessão para confirmar acesso.
   Em seguida, relistar/testar URLs atuais. Registrar idade, respostas e esforço.
3. Após o expireTime real da sessão, renovar OAuth separadamente e repetir consulta,
   URLs iniciais e listagem. Considerar 403/404 com suas ambiguidades; não apagar
   ou recriar a seleção para produzir o resultado. Eventual reseleção e revogação
   devem ser ensaios distintos, posteriores à observação, conforme a issue.

O resultado antes/depois ainda falta; G4 permanece em validação, issue aberta e
contador do plano em 3/6. A [sequência revisada aprovada](plans/02-viabilidade-google-fotos.md#3-sequência-e-decisões-de-passagem)
permite G5 durante essa espera, preservando separadamente a amostra G4. Retomar
as medições G4 quando suas fronteiras ocorrerem.

## 9. Escala e coleções G5

Iniciada a preparação de [G5 / #46](https://github.com/RogerVFbr/memora/issues/46)
após G3 aceita, conforme a revisão de sequência aprovada no
[plano §3](plans/02-viabilidade-google-fotos.md#3-sequência-e-decisões-de-passagem).
G4 permanece em validação, com sua amostra preservada. O proprietário dispõe de
álbuns grandes, mas ainda não sabe suas quantidades; isso não comprova origem
acima de 2.000 itens. Confirmar o tamanho e a seleção na UI do Picker antes do aceite.

O ensaio é separado: duas seleções A/B, catálogo mínimo cifrado e nenhuma leitura
de URL/thumbnail/bytes de mídia. Consultar somente JSON Picker e descartar URLs,
nomes de arquivos, valores de datas/câmera e outros metadados pessoais ao processar
a página; reter IDs para comparação de interseção, tipo e indicadores/inventário
de campos. Arquivo, chave e AAD próprios não alteram o snapshot G4.

A amostra será escolhida pelo proprietário após apresentar o recorte privado na
tela. Medir contagens recebidas/únicas, duplicatas internas, interseção entre
seleções, páginas, requisições, bytes JSON e duração; comparar esses resultados
com a quantidade e o comportamento informados por ele na UI do Google. Sem
origem acima do limite ou seleção real suficiente, manter o aceite correspondente
pendente. IDs repetidos não demonstram acesso permanente nem membership de álbum.

### 9.1 Instrumentação e validação inicial

Activity, core e armazenamento próprios implementados pelo
[PR #60](https://github.com/RogerVFbr/memora/pull/60), com limites de 2.000 itens,
páginas de 100, 128 páginas, 256 requisições normais e reserva para DELETE,
2 MiB por corpo JSON e 32 MiB por seleção. Respostas e listagem têm orçamento
de tempo; atingir limite deixa a travessia incompleta. Cancelamento usa sinal
explícito pré-worker. Intenção POST e ID retornado são preservados para limpeza;
transporte incerto bloqueia criação cega. Nenhum endpoint de mídia é aberto.

Build debug, 32 testes, lint e geração de manifesto/recursos release aprovados.
Testes adicionais cobrem limite, paginação curta/vazia, repetição de token,
interseção/deduplicação, limites de resposta e cancelamento. Manifesto release
continua sem ensaios nem INTERNET. SSP e callbackPath isolam o retorno G5, mantendo
o caminho original para G2–G4: resolução real de ambos no aparelho apontou para
suas respectivas Activities, sem chooser.

Instalação de atualização aprovada; hash do arquivo cifrado G4 confirmou igualdade
bit a bit com a referência anterior. Abertura G5 sem rede mostrou slots A/B
ausentes. OAuth real G5 retornou para a nova Activity, criou a sessão A e mostrou
QR nativo. Envelope cifrado separado confirmado, sem marcadores de token/URI
legíveis; G4 continuou bit a bit igual após essa criação. Seleção pessoal ainda
não recebida; nenhum aceite operacional.

Remover apenas sessões e dados G5 ao fim do ensaio ou a pedido, preservando G4
até a observação de expiração. DELETE confirmado mantém o catálogo mínimo até
comparação; apagamento explícito posterior remove arquivo/chave G5. Se o token
expirar antes da listagem/limpeza, será necessário tratar renovação separada;
falha ou 404 não comprovam remoção remota.
