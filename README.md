# memora
Photo visualization application

## Protótipo visual local

Requer Node.js `^20.19.0` ou `>=22.12.0`, com npm. Na raiz do repositório:

```sh
npm --prefix apps ci
cd apps/prototype
npm run dev
```

Abra http://127.0.0.1:5173/ no navegador. O servidor escuta somente a máquina
local e exige essa porta livre. Compare os temas no topo, escolha as composições
abaixo da apresentação e abra **Laboratório** para simular estados.

Na apresentação, use setas para navegar, Enter para selecionar/abrir controles,
Escape para voltar e Espaço para play/pausa quando o viewport estiver em foco.
Mouse também funciona. **Tela cheia** amplia somente a experiência da TV.
Os ajustes são salvos neste navegador; **Reiniciar demonstração**, no laboratório,
restaura os padrões do protótipo. Para encerrar o servidor, use `Ctrl+C` no terminal.

No mesmo diretório, valide TypeScript e gere os arquivos estáticos:

```sh
npm run build
npm run preview
```

O preview do build abre em http://127.0.0.1:4173/. Build e preview não instalam APK.
Consulte [identidade visual e UX](docs/01-identidade-visual-e-ux.md#1-papel-deste-documento)
para decisões, proveniência da mídia, limites do mock e roteiro de avaliação.

## Diagnóstico local do Fire Stick

Requer Python 3.9+ e Android Platform Tools (`adb` no PATH). No macOS:

```sh
brew install --cask android-platform-tools
```

Copie `.agents/skills/firestick-check/firestick.example.json` para
`.local/firestick.json` (crie `.local/` se necessário) e substitua o endereço
fictício pelo IPv4 do Fire Stick, seguido de `:5555`. O arquivo local é ignorado
pelo Git. Como alternativa, defina `FIRESTICK_ADB_ADDRESS` no ambiente.

Execute na raiz do repositório:

```sh
python3 .agents/skills/firestick-check/scripts/check_firestick.py
```

Acrescente `--screenshot` para salvar uma captura em diretório temporário fora
do repositório. A saída do diagnóstico e as imagens são privadas: não publique
esses resultados no Git nem em issues. Procedimento completo na
[skill firestick-check](.agents/skills/firestick-check/SKILL.md).

## Host Android local

Requer JDK 17 (`JAVA_HOME`), [Android SDK](https://developer.android.com/studio#command-line-tools-only)
(`ANDROID_HOME`), plataforma
`platforms;android-35` e Build Tools `35.0.0`. Instale os pacotes com
`cmdline-tools/19.0/bin/sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-35" "build-tools;35.0.0"`
e aceite as licenças do SDK. O Gradle Wrapper baixa a distribuição fixada;
a primeira execução também requer acesso aos repositórios Google/Maven Central.
Não é necessário instalar Gradle globalmente ou manter servidor de frontend.
Node/npm e as dependências dos workspaces também são necessários para gerar os assets.

Na raiz, com as variáveis apontando às instalações locais:

```sh
apps/android/gradlew -p apps/android assembleDebug testDebugUnitTest lintDebug
```

APK de desenvolvimento: `apps/android/app/build/outputs/apk/debug/app-debug.apk`
(pacote `br.com.memora.dev`). Relatórios ficam em
`apps/android/app/build/reports/`, incluindo `tests/testDebugUnitTest/index.html`
e `lint-results-debug.html`. O comando compila os assets de TV e o APK e verifica localmente; instalação
não faz parte dele. Execute `npm --prefix apps ci` na raiz antes do primeiro build Android.
O APK contém a experiência demonstrativa completa, sem laboratório.
Consulte [host e baseline](docs/02-host-android-e-baseline.md#1-host-e-fronteira)
para segurança, lifecycle, versões fixadas e limites da validação.

## Frontend demonstrativo de TV

Os workspaces são definidos em `apps/package.json`, com lockfile e dependências
em `apps/`. `apps/shared/presentation` contém o código compartilhado pelos dois
frontends; `apps/node_modules` é gerado pelo npm e ignorado pelo Git. Na raiz do
repositório:

```sh
npm --prefix apps ci
npm --prefix apps run build:prototype
npm --prefix apps run build:tv
npm --prefix apps run preview --workspace @memora/tv
```

A entrada de TV compilada fica em `apps/tv/dist/`; o preview usa
http://127.0.0.1:4174/. Os controles e painéis compartilham a implementação com
o laboratório, mas ocupam a janela inteira. Em **Ajustes**, **Aparência** troca o
tema. A primeira entrada é pausada, com controles visíveis e Cinema acolhedor;
reabrir preserva os ajustes e mantém a reprodução pausada. Para encerrar, `Ctrl+C`.
O Gradle gera sua própria cópia dos assets por variante, com a versão do APK;
o preview avulso exibe desenvolvimento e não instala nada no dispositivo.

Para verificar estados de reprodução e leitura/gravação das preferências, na raiz:

```sh
npm --prefix apps run test:presentation
```

O comando compila os módulos testados para `.local/test-presentation/` e executa
os testes locais do Node. Na entrada de TV, **esquerda/direita** ajusta a opção
em foco e **OK** avança para a seguinte; **cima/baixo** navega entre os ajustes.
O mapeamento e o estado de validação no controle físico estão no
[contrato da TV](docs/02-host-android-e-baseline.md#5-controle-persistência-e-lifecycle-da-tv).

### Instalação de desenvolvimento

Depois de verificar o dispositivo pelo diagnóstico acima e consultar o escopo
de operação no [AGENTS.md](AGENTS.md#fluxo-de-trabalho-no-repositório),
substitua `<alvo-adb>` pelo endereço local configurado e execute na raiz:

```sh
adb -s <alvo-adb> install apps/android/app/build/outputs/apk/debug/app-debug.apk
```

Esse comando foi validado para a primeira instalação do pacote de desenvolvimento.
Antes de executar e validar o aplicativo, consulte as
[autorizações vigentes do Fire Stick](AGENTS.md#fluxo-de-trabalho-no-repositório).
O comando validado para iniciar o APK de desenvolvimento é:

```sh
adb -s <alvo-adb> shell am start -n br.com.memora.dev/br.com.memora.MainActivity
```

Você pode acompanhar a execução na TV, usando a entrada HDMI do Fire Stick.
Se o aparelho estiver em repouso, desperte-o pelo controle. Os testes de
navegação podem ser realizados manualmente no controle enquanto os diagnósticos
consultam o aplicativo em leitura.

No teste de reabertura com processo novo, encerre o aplicativo antes de iniciá-lo
novamente com o comando acima:

```sh
adb -s <alvo-adb> shell am force-stop br.com.memora.dev
```

Exemplos de teclas usados na validação operacional:

```sh
adb -s <alvo-adb> shell input keyevent 82
adb -s <alvo-adb> shell input keyevent 85
```

Os exemplos enviam, respectivamente, Menu e Play/Pause. Consulte o
[contrato da TV](docs/02-host-android-e-baseline.md#5-controle-persistência-e-lifecycle-da-tv)
para estados, evidências e limites dos testes.

## Empacotamento local da release

Requer os pré-requisitos Android/Node acima e Python 3.9+. No macOS, a custódia
usa o Acesso às Chaves. Prepare a identidade somente uma vez, com um diretório
privado de backup em outro volume e fora do repositório:

```sh
python3 -m tools.android_signing prepare --backup-dir /Volumes/<volume>/.memora-signing-backup
python3 -m tools.android_signing check
```

A ferramenta gera o keystore fora do Git e registra apenas o fingerprint público.
Consulte [custódia](docs/03-entrega-android.md#2-custódia-aprovada) antes de restaurar
uma identidade existente ou cuidar do backup da senha. Não gere outra chave para
atualizar uma aplicação já assinada.

Na raiz, valide os contratos da entrega:

```sh
python3 -m unittest discover -s tools/tests -v
```

Com `JAVA_HOME` e `ANDROID_HOME` configurados, use um checkout limpo de fonte já
integrada em `origin/main` para gerar e conferir os quatro entregáveis:

```sh
python3 -m tools.android_release build
python3 -m tools.android_release verify builds/0.1.0-alpha.1
```

A versão corrente vem de `android-release.properties`; ajuste o argumento do
verificador ao conferir outra entrega ou uma cópia obtida da Release. `build`
executa `npm --prefix apps ci`, testes da apresentação e build/testes/lint de release; não
precisa de servidor Vite. `verify` precisa do SDK/JDK, sem acesso ao keystore ou
à senha. Configuração de assinatura ausente/inconsistente e destino já existente
causam falha sem substituir arquivos. Os comandos não publicam nem instalam APKs.

## Publicação e obtenção da release

Requer GitHub CLI autenticado. Após gerar a entrega de fonte integrada, o fluxo
validado da primeira release foi (substitua `<commit-origem>` pelo commit
registrado no `build-info.json`):

```sh
git tag -a v0.1.0-alpha.1 <commit-origem> -m "Memora 0.1.0-alpha.1"
git push origin v0.1.0-alpha.1
gh release create v0.1.0-alpha.1 builds/0.1.0-alpha.1/* --repo RogerVFbr/memora --verify-tag --draft --prerelease --title "Memora 0.1.0-alpha.1" --notes-file builds/0.1.0-alpha.1/release-notes.md
gh release download v0.1.0-alpha.1 --repo RogerVFbr/memora --dir .local/release-downloads/0.1.0-alpha.1
python3 -m tools.android_release verify .local/release-downloads/0.1.0-alpha.1
```

Tags e diretórios existentes não devem ser recriados. Para obter uma versão já
publicada, execute somente o download para um diretório novo e a verificação.
Consulte a [central do plano](docs/plans/01-primeira-versao-instalavel.md#5-central-de-progresso)
para releases disponíveis e evidências. Ajuste os identificadores para a entrega
pretendida, preservando os bytes de pré-releases publicadas.

Após `firestick-check` e a consulta ao escopo do AGENTS.md, a primeira instalação
da identidade de release e sua abertura pelo launcher foram validadas com:

```sh
adb -s <alvo-adb> install .local/release-downloads/0.1.0-alpha.1/memora-0.1.0-alpha.1.apk
adb -s <alvo-adb> shell am start -W -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n br.com.memora/br.com.memora.MainActivity
```

Somente depois do aceite de hardware, publique o rascunho como pré-lançamento.
O arquivo de notas operacionais é local e deve conter apenas resultados
sanitizados; os quatro anexos permanecem os mesmos:

```sh
gh release edit v0.1.0-alpha.1 --repo RogerVFbr/memora --draft=false --prerelease --notes-file <notas-operacionais.md>
```

A atualização da segunda entrega foi validada após baixar e verificar os seus
quatro anexos pelo mesmo fluxo. Mantendo a instalação e seus dados:

```sh
adb -s <alvo-adb> install -r .local/release-downloads/0.1.0-alpha.2/memora-0.1.0-alpha.2.apk
```

A identidade de abertura permanece a mesma. Consulte o resultado da matriz de
preservação na [issue I6](https://github.com/RogerVFbr/memora/issues/12) antes de
usá-lo como evidência de compatibilidade; esta entrega preserva o esquema v1 e
não comprova migrações futuras.


## Ensaio OAuth Picker de desenvolvimento

Requer cliente OAuth Android de teste exportado em
`.local/google/oauth-android-dev.json` (ignorado pelo Git), associado ao pacote e
certificado do APK debug. Somente o client ID público é incorporado à variante
debug; arquivo ausente deixa a tela sem configuração. Não usar exportação com
client secret. O [registro do ensaio](docs/04-viabilidade-google-fotos.md#6-ensaio-oauth-nativo-de-desenvolvimento)
concentra desenho, limites e estado da validação.

Com os pré-requisitos Android/Node acima, executar na raiz:

```sh
apps/android/gradlew -p apps/android assembleDebug testDebugUnitTest lintDebug
```

Após o diagnóstico `firestick-check` e usando seu alvo privado:

```sh
adb -s <alvo-adb> install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
adb -s <alvo-adb> shell am start -W -n br.com.memora.dev/br.com.memora.OAuthSpikeActivity
```

Escolher **Iniciar autorização Google** pelo controle. O navegador Amazon da TV
abre o consentimento; retorno válido verifica o escopo criando/removendo uma sessão
vazia, sem selecionar mídia. **Cancelar tentativa** invalida o retorno pendente;
perder o processo exige recomeçar. Tokens não são persistidos. Sem cliente local,
login ou limpeza de sessão concluídos, não interpretar o build como aceite OAuth.
A tela e a permissão de rede só existem no debug; artefatos contendo o cliente
local devem permanecer privados nesta investigação.


### Seleção diagnóstica SPK-02

Na mesma Activity de ensaio, **SPK-02: selecionar 10 fotos e 2 vídeos** inicia
OAuth e mostra QR nativo para o Picker. A conta usada no celular deve ser a mesma
autorizada na TV. Selecione exatamente a amostra combinada e conclua no Picker;
a TV consulta a sessão somente enquanto visível, respeitando os intervalos
retornados, percorre páginas de cinco itens e mostra resumo técnico.

O [recorte e a retenção](docs/04-viabilidade-google-fotos.md#7-seleção-e-recuperação-diagnóstica-g3)
explicam limites de bytes e preservação cifrada para G4. Não há apresentação de
mídia, download completo de vídeos ou publicação. **Cancelar tentativa** limpa
a sessão incompleta; **Limpar sessão e evidência privada** descarta o registro
preservado quando autorizado ao fim do ensaio. Não limpe o resultado positivo
antes das observações de expiração de G4.

### Expiração diagnóstica SPK-03

Reutilize o build, instalação `-r` e Activity dos ensaios acima; preserve os dados
do aplicativo. Com a amostra G3 existente, os botões **SPK-03** executam operações
separadas: consultar a sessão, testar URLs iniciais sem relistar, relistar/testar
URLs atuais e consultar com o OAuth original. **Renovar OAuth sem nova seleção**
abre o consentimento e substitui a credencial ativa, preservando a sessão e as
âncoras originais. Não use o botão SPK-02 para renovar autorização.

**SPK-03: renovar token sem navegador** tenta o grant de refresh atual, sem nova
seleção ou consentimento. A tela informa ausência/recusa sem retry automático;
um sucesso ainda deve ser conferido por **Consultar sessão**. Informações locais
mostram somente a presença do refresh token, nunca seu valor.

Para o ensaio de processo novo, use o `force-stop` da
[instalação de desenvolvimento](#instalação-de-desenvolvimento) e reabra
`OAuthSpikeActivity` pelo comando da seção OAuth acima; não limpe dados.

**Histórico de expiração** lê o registro local sem rede. Abrir novamente o app
não inicia medições. Os [procedimentos e pendências G4](docs/04-viabilidade-google-fotos.md#8-expiração-e-reabertura-g4)
definem quando medir; não limpe dados, desinstale nem revogue o acesso antes das
observações naturais. O build não comprova expiração real.

### Escala e coleções SPK-04

G5 usa o mesmo build debug, mas uma Activity e armazenamento próprios. Após
`firestick-check` e a instalação `-r`, execute:

```sh
adb -s <alvo-adb> shell am start -W -n br.com.memora.dev/br.com.memora.ScaleSpikeActivity
```

**Criar ou retomar seleção A/B** abre o OAuth/QR de cada amostra. A seleção pelo
proprietário consulta somente metadados, até 2.000 itens por sessão. Depois de
concluir no Picker, use **Listar metadados A/B**: a ação consulta uma vez o estado
da sessão e percorre páginas de até 100 itens. **Comparar seleções offline** e
**Informações locais** não acessam a rede.

**Remover sessões remotas** exige DELETE confirmado e preserva o catálogo mínimo
para comparação; **Apagar evidência local** remove arquivo/chave G5 depois dessas
remoções. O [protocolo G5](docs/04-viabilidade-google-fotos.md#9-escala-e-coleções-g5)
define amostra, evidências e pendências. Esses comandos não limpam G4.
