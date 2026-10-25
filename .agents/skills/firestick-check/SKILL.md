---
name: firestick-check
description: Verifique conectividade ADB, autorização, identidade e estado do Fire TV do Memora antes de deploy, testes no hardware ou diagnóstico do dispositivo.
---

# Fire Stick do Memora

Use esta skill no repositório Memora. Execute o diagnóstico antes de operações
no Fire Stick; uma conexão anterior não comprova disponibilidade atual.

## Diagnóstico

Na raiz do repositório, execute:

```sh
python3 .agents/skills/firestick-check/scripts/check_firestick.py
```

O script usa Python 3 e `adb` no PATH. Endereço em ordem de precedência:
`--address`, variável `FIRESTICK_ADB_ADDRESS`, arquivo privado
`.local/firestick.json`. O exemplo `firestick.example.json` contém endereço
fictício; copie e ajuste localmente quando necessário. Não executar nem fazer
`source` de arquivos de configuração: o formato é JSON.

- Verifica TCP, tenta uma conexão ADB e consulta somente o alvo com `adb -s`.
- Verifica Amazon / modelo AFTKRT / dispositivo karat. Isso identifica o tipo
  esperado, não autentica um aparelho individual nem comprova a geração comercial.
- Consulta Android, WebView, tela, Activity em foco, RAM e espaço em `/data`.
- Retorna JSON no terminal: código 0 para identidade e consultas completas;
  1 para falha de conexão, autorização ou identidade; 2 para configuração inválida
  ou ferramenta ausente; 3 para diagnóstico parcial. Campos indisponíveis não
  significam estado saudável.
- Cada comando tem timeout; não há polling, varredura da rede ou retry infinito.

## Interpretar e recuperar

- `unauthorized`: peça ao usuário para confirmar a autorização na TV.
- `offline` ou conexão indisponível: confira energia, mesma rede, IP atual e
  Depuração ADB. O IP pode mudar por DHCP; não concluir que o aparelho inexiste.
- Identidade divergente: interrompa deploy/testes e esclareça qual é o aparelho.
- ADB ausente no Mac: instalação conhecida é
  `brew install --cask android-platform-tools`; instale apenas no escopo autorizado.
- Não matar o servidor ADB global, apagar chaves, reiniciar a TV, instalar APKs
  ou alterar configurações como parte deste diagnóstico.

## Captura e espelhamento

Quando a tarefa exigir ver a tela, use `--screenshot`. A captura é opcional e
salva em diretório temporário privado fora do repositório, nunca em fixtures.
Ela pode conter contas e fotos pessoais; não publique nem anexe automaticamente.

Conexão ADB, captura PNG e espelhamento são capacidades diferentes. Já foi
observada captura funcional neste hardware, mas o scrcpy não confirmou imagem
ao vivo. Não declarar espelhamento validado apenas por iniciar o processo.
Se solicitado, testar scrcpy separadamente, com tempo limite, informando o resultado.

## Privacidade e escopo

Endereço real, resultados, capturas e logs ficam locais. Não copiar configuração
privada nem saída bruta para commits, PRs ou documentação pública. Nunca ler ou
versionar `~/.android/adbkey` ou sua chave pública. `.gitignore` é prevenção,
não substitui revisão dos arquivos staged. Não encaminhar a porta ADB na internet.

Esse diagnóstico não valida OAuth/Google Fotos nem conclui SPK-05/SPK-06:
instalação de APK, codecs, controle remoto, lifecycle e desempenho continuam
exigindo testes específicos do PRD.

Referência: [ADB no Fire TV](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html).
