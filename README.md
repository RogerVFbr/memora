# memora
Photo visualization application

## Protótipo visual local

Requer Node.js `^20.19.0` ou `>=22.12.0`, com npm. Na raiz do repositório:

```sh
npm ci
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
não faz parte dele. Execute `npm ci` na raiz antes do primeiro build Android.
O APK contém a experiência demonstrativa completa, sem laboratório.
Consulte [host e baseline](docs/02-host-android-e-baseline.md#1-host-e-fronteira)
para segurança, lifecycle, versões fixadas e limites da validação.

## Frontend demonstrativo de TV

Os workspaces usam um único `package-lock.json` na raiz. Na raiz do repositório:

```sh
npm ci
npm run build:prototype
npm run build:tv
npm run preview --workspace @memora/tv
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
npm run test:presentation
```

O comando compila os módulos testados para `.local/test-presentation/` e executa
os testes locais do Node. Na entrada de TV, **esquerda/direita** ajusta a opção
em foco e **OK** avança para a seguinte; **cima/baixo** navega entre os ajustes.
O mapeamento e o estado de validação no controle físico estão no
[contrato da TV](docs/02-host-android-e-baseline.md#5-controle-persistência-e-lifecycle-da-tv).

### Instalação de desenvolvimento

Depois de verificar o dispositivo pelo diagnóstico acima e obter a autorização
específica exigida pelo [AGENTS.md](AGENTS.md#fluxo-de-trabalho-no-repositório),
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
executa `npm ci`, testes da apresentação e build/testes/lint de release; não
precisa de servidor Vite. `verify` precisa do SDK/JDK, sem acesso ao keystore ou
à senha. Configuração de assinatura ausente/inconsistente e destino já existente
causam falha sem substituir arquivos. Os comandos não publicam nem instalam APKs.
