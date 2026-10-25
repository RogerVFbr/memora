---
name: plan-work
description: Planeje uma tarefa ampla do Memora, esclareça decisões abertas, registre o plano em docs/plans e publique suas issues após validar o recorte com o desenvolvedor. Use para criar ou revisar um plano de trabalho e sua decomposição; executar uma issue ou retomar implementação pertence à implement-issue.
---

# Planejar trabalho no Memora

Transforme um objetivo em um plano aprovado e issues executáveis, com central
de progresso e documentação pronta para a próxima janela de contexto.
O resultado desta skill é a entrega do planejamento, não a implementação do plano.

## Recuperar as fontes

Resolva a raiz do checkout e leia o [AGENTS.md](../../../AGENTS.md). Use seu
roteiro para consultar o PRD e aplique as seções de
[planejamento](../../../AGENTS.md#planejamento-e-retomada-de-contexto),
[manutenção documental](../../../AGENTS.md#manutenção-da-documentação) e
[fluxo por issues](../../../AGENTS.md#desenvolvimento-orientado-por-issues).
Essas fontes definem as regras; não as reproduza no plano ou em novos procedimentos.

Confira o Git e preserve alterações existentes. Leia código e documentação
pertinentes e consulte planos, issues, comentários e PRs atuais antes de propor
nova decomposição. Se o trabalho já estiver coberto, atualize e vincule os itens
existentes em vez de criar um plano ou issues concorrentes.

O [plano instalável](../../../docs/plans/01-primeira-versao-instalavel.md) é um
exemplo de central de progresso. Aproveite sua organização quando útil, sem
transferir seu escopo, versões, tecnologias ou número de etapas para outro objetivo.

## Validar objetivo e recorte

Identifique resultado esperado, limites de escopo, restrições, relação com IDs
do PRD e evidências que demonstrarão sucesso. Separe decisões de produto,
escolhas técnicas e hipóteses que exigem experimentos.

Investigue fatos nas fontes disponíveis; pergunte ao desenvolvedor apenas sobre
decisões ainda abertas que mudem o plano. Dê recomendações e explique o impacto
das alternativas. Não reabra decisões consolidadas sem evidência nova nem exija
outra entrevista quando o recorte já estiver aprovado na tarefa.

Registre propostas como propostas. A autorização permanente de Git/GitHub não
aprova o escopo de um novo plano: antes de publicar suas issues de implementação,
obtenha a validação das decisões em aberto. Uma resposta pendente não é aprovação.
Enquanto aguarda, avance na pesquisa e no rascunho que não dependam dessa decisão.

## Preparar plano e decomposição

Crie ou revise o documento apropriado em `docs/plans/`, com estado e aprovação
explícitos. Siga o conteúdo exigido pelo AGENTS.md e referencie as definições
existentes. Não descreva comandos, diretórios ou integrações propostos como
implementados. Atualize o PRD somente quando houver decisão de produto ou
técnica pertinente, mantendo as referências consistentes.

Divida a entrega em issues com fronteiras claras e tamanho que permita revisão
e aceite próprios. Não use uma quantidade fixa de issues. Para cada etapa,
prepare contexto, escopo, dependências, critérios observáveis, validações
previstas e referências às fontes. Inclua trabalho de integração, publicação e
validação operacional quando necessário ao objetivo, sem escondê-lo sob um
critério genérico de "build aprovado".

Confira a sequência: cada dependência deve fornecer o que a etapa seguinte
precisa; critérios de código e de operação devem distinguir evidência já obtida
daquela que só existirá após instalação, publicação ou uso real. Hipóteses
bloqueadoras recebem investigação explícita, não promessa de implementação.

## Publicar e consolidar

Com o recorte validado, publique as issues uma a uma pelo fluxo autorizado no
AGENTS.md, preservando ordem e vínculos. Registre imediatamente os números e
URLs retornados na central do plano. Em caso de publicação parcial ou resposta
incerta, confira o GitHub antes de repetir; retome pelos itens faltantes sem
duplicar os que já existem. Informe o que foi publicado e o que ainda falta.

Mantenha o detalhamento executável nas issues; o plano guarda decisões,
sequência, dependências, status, PRs, evidências, pendências e próximo passo.
Defina como atualizar essa central em cada conclusão, distinguindo integração
de código de aceite operacional. Não marque implementação iniciada ou concluída
apenas por publicar as issues ou integrar o documento.

## Revisar e entregar

Revise se as issues cobrem o objetivo, se há lacunas ou duplicações, se cada
aceite pode ser verificado e se a próxima etapa é inequívoca. Confira links e
âncoras locais, vínculos no GitHub e o diff documental. Nenhum teste de hardware
é presumido por essa revisão; quando necessário, consulte as regras e skills
indicadas pelo AGENTS.md.

Conclua o assunto documental com a issue de planejamento e o ciclo de entrega
definido no AGENTS.md, respeitando a
[autorização permanente](../../../AGENTS.md#autorização-permanente-para-conclusão-de-trabalho).
O PR do planejamento deve encerrar apenas o assunto entregue, não as futuras
issues de implementação. Registre validações e limitações reais; uma revisão
documental não comprova viabilidade técnica nem comportamento em nova sessão.

Ao terminar, informe o plano integrado, as issues publicadas e a próxima etapa.
Indique o [prompt de retomada](../../../docs/prompts/implementar-proxima-etapa.md)
e a [implement-issue](../implement-issue/SKILL.md) para execução posterior.
Não inicie essa implementação nem altere o aparelho ao concluir o planejamento.
