# memora
Photo visualization application

## Protótipo visual local

Requer Node.js `^20.19.0` ou `>=22.12.0`, com npm. Na raiz do repositório:

```sh
cd apps/prototype
npm ci
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
Não é necessário instalar Gradle globalmente ou executar o frontend.

Na raiz, com as variáveis apontando às instalações locais:

```sh
apps/android/gradlew -p apps/android assembleDebug testDebugUnitTest lintDebug
```

APK de desenvolvimento: `apps/android/app/build/outputs/apk/debug/app-debug.apk`
(pacote `br.com.memora.dev`). Relatórios ficam em
`apps/android/app/build/reports/`, incluindo `tests/testDebugUnitTest/index.html`
e `lint-results-debug.html`. O comando compila e verifica localmente; instalação
não faz parte dele. A página desta etapa é apenas um baseline estático.
Consulte [host e baseline](docs/02-host-android-e-baseline.md#1-host-e-fronteira)
para segurança, lifecycle, versões fixadas e limites da validação.
