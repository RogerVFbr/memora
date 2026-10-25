# memora
Photo visualization application

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
