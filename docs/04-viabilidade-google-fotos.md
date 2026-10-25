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

A [avaliação G1](#51-g1--enquadramento-e-arquitetura-candidata) compara os caminhos
e registra condições de passagem. Nenhuma arquitetura está aprovada para implantação.

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
| Web verdadeiro + pareamento | Navegador do celular → Google → callback HTTPS registrado no serviço; troca e custódia no servidor; APK pareado por canal autenticado | Único candidato descrito aqui com separação plausível do OAuth no Fire OS sem GMS, mas não aprovado: exige serviço real, revisão de associação à TV e decisão específica de custo/privacidade. Não equivale a embutir cliente web no APK. |

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

#### Resultado e condição de retomada

A investigação produziu mapa e inventário, mas não satisfez a passagem para G2:
R-013 continua sem esclarecimento e não há caminho aprovado de autorização e
obtenção de mídia. G1 permanece aberta/bloqueada; não é inviabilidade conclusiva.
Retomar #42 com resposta oficial sobre enquadramento e decisão específica do
proprietário sobre investigar infraestrutura/fronteira de dados/custo. Depois,
confirmar configurações externas em ambiente privado e revisar a passagem.
Sem resposta ou solução justificável, levar os impedimentos à decisão G6 em
retomada expressamente autorizada. Nenhuma etapa seguinte executada aqui.
