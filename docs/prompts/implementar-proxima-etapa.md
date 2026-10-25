# Retomar e implementar a próxima etapa

Copie o bloco abaixo e cole em uma nova janela de contexto. Ele recupera o estado
nas fontes atuais, sem precisar desta conversa. Para escolher entre vários planos,
acrescente à mensagem o caminho do plano desejado.

```text
Trabalhe no repositório /Volumes/ACASISHD1/repos/memora.
Esta é uma nova janela de contexto: recupere o estado do projeto pelos arquivos
e pelo GitHub, sem presumir decisões ou ações de conversas anteriores.

Leia primeiro AGENTS.md, incluindo Planejamento e retomada de contexto,
Desenvolvimento orientado por issues e a autorização permanente de conclusão.
Use o roteiro desse arquivo para consultar docs/00-PRD.md e cumpra a leitura
integral exigida antes de gerar código.

Localize o plano aprovado aplicável em docs/plans, conforme as regras de seleção
do AGENTS.md. Leia seu recorte, dependências e central de progresso. Confira
Git, remoto, issues, comentários, PRs e evidências atuais para identificar a
etapa a retomar ou a próxima issue elegível. Não confunda issue fechada com
aceite demonstrado nem abra outra branch/PR se já houver trabalho a retomar.

Use a skill implement-issue. Se ela não estiver disponível no catálogo, leia
diretamente .agents/skills/implement-issue/SKILL.md e siga suas instruções.
Informe brevemente qual plano e issue vai executar, com título e dependências,
e prossiga sem reabrir decisões aprovadas. Se as fontes não permitirem uma
escolha inequívoca, esclareça somente o ponto que impede a seleção.

Execute UMA etapa, respeitando o escopo e o aceite da issue. Consulte os arquivos
referenciados pelo plano, os comandos reais do README e as skills pertinentes.
Conclua o ciclo de validação, atualização do plano, commit, push, atualização da
issue, PR e merge conforme AGENTS.md; não pare no commit ou no PR aberto quando
a integração estiver autorizada e apta. As autorizações específicas do Fire Stick
continuam sendo as definidas no AGENTS.md.

Ao finalizar, registre o resultado na central de progresso e na issue, confira
o estado final do Git e informe evidências, links, limitações e a próxima etapa
indicada pelo plano. Não inicie a issue seguinte nesta janela. Se houver um
impedimento real, preserve o trabalho, registre o estado verdadeiro e explique
a ação necessária para retomar, sem declarar aceite ou conclusão inexistentes.
```

As regras permanentes têm fonte única no
[AGENTS.md](../../AGENTS.md#planejamento-e-retomada-de-contexto); a execução é
orientada pela [skill implement-issue](../../.agents/skills/implement-issue/SKILL.md).
Planos e issues devem permanecer atualizados para que o mesmo prompt sirva às
próximas retomadas. Este arquivo não é um registro de progresso.
