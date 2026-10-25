# Viabilidade Google Fotos — fontes e evidências

> Data da consulta: 2026-10-25 · Estado: pesquisa documental; ensaios não iniciados.
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

G1 comparará caminhos candidatos com fonte oficial: autorização apropriada à
plataforma, navegador externo disponível, retorno ao cliente e eventual serviço
de pareamento. Não há arquitetura selecionada nesta pesquisa.

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

Nenhum ensaio operacional Google foi executado. Acrescentar subseções por etapa
quando houver issue e resultados reais, com links para evidências sanitizadas.
A síntese final deve apontar decisões do PRD que mudaram e riscos que continuam
abertos; não declarar o gate concluído somente por existir este documento.
