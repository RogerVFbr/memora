# Entrega Android local e custódia de assinatura

Este documento descreve a implementação de I4 / [#10](https://github.com/RogerVFbr/memora/issues/10).
Consulte o [plano §4](plans/01-primeira-versao-instalavel.md#4-semver-assinatura-e-artefatos)
para convenções e requisitos da entrega, o
[README](../README.md#empacotamento-local-da-release) para comandos reais e o
[AGENTS.md](../AGENTS.md#fluxo-de-trabalho-no-repositório) antes de operar o Fire Stick.
O pipeline abaixo não publica nem instala aplicativos.

## 1. Configuração e fronteiras

[android-release.properties](../android-release.properties) é a fonte única de
application ID, SemVer e contador Android. Gradle lê esse arquivo, acrescentando
o sufixo de desenvolvimento somente na variante debug. A versão efetiva da
variante é injetada nos assets de TV. O fingerprint SHA-256 do certificado
permanente está em [android-release-signing.sha256](../android-release-signing.sha256),
sem material privado.

O módulo [android_release](../tools/android_release.py) aceita somente versão e
identidade válidas, checkout limpo e commit integrado em `origin/main` antes de
gerar uma entrega final. Recusa versão/tag/diretório já usados; contador Android
precisa superar entregas locais e tags anteriores, independentemente da ordem
textual da SemVer. Pacote e certificado precisam permanecer consistentes.

O build instala dependências pelo lockfile, roda os testes de apresentação e
executa release/testes/lint localmente. Credenciais chegam ao Gradle por ambiente,
sem argumentos de senha; não são repassadas às ferramentas do frontend. A variante
release tem configuração de assinatura obrigatória, sem fallback para debug.

A composição é preparada em diretório temporário de `builds/`, verificada e
copiada para um destino novo com criação exclusiva, sem sobrescrever entregas.
Uma falha de cópia pode deixar um diretório incompleto: ele não é considerado
verificado e a próxima execução o recusa. Investigar a falha antes de remover
esse resíduo local; nunca substituir uma pré-release publicada.

## 2. Custódia aprovada

O desenvolvedor aprovou keystore PKCS#12 cifrado fora do repositório no Mac,
senha no Acesso às Chaves e cópia cifrada em outro volume, também fora do Git.
O proprietário é o desenvolvedor. A preparação pelo módulo
[android_signing](../tools/android_signing.py) exige backup em volume diferente,
permissões privadas e igualdade dos bytes do keystore e de sua cópia.

A chave fica em `~/.local/share/memora/signing/release.p12`. Os caminhos reais e
os identificadores do Chaves ficam em `.local/signing.json`, privado e ignorado.
O serviço do Chaves é `br.com.memora.release-signing`, conta `memora-release`.
Não há senha nesse JSON: a ferramenta a recupera internamente e verifica o
certificado antes de usar. A preparação não substitui chave, backup ou pin
existentes por outra identidade.

O backup do PKCS#12 depende da senha para recuperação. O desenvolvedor assumiu a
custódia do backup pessoal dessa credencial, recuperável pelo Acesso às Chaves;
a senha não deve ser incluída nos artefatos, no Git ou em logs. Para restaurar
em outra máquina, recuperar a chave e a credencial existentes antes de preparar
a configuração local; não gerar uma identidade nova para atualizar a aplicação.

A chave usa RSA 3072 e certificado de longa duração para a sequência de releases.
Somente o fingerprint público é versionado. O módulo de custódia não opera sobre
o Fire Stick nem requer instalar software adicional no dispositivo.

## 3. Verificação de artefatos

O diretório final contém exatamente os quatro arquivos definidos no plano.
`build-info.json` registra schema, identidade/versão, commit, horário UTC,
versões das ferramentas, fingerprint público e hash do APK final. Não guarda
caminhos locais, credenciais ou diagnóstico de dispositivo. As notas vêm do
arquivo versionado em `docs/releases/<semver>.md`, com a origem acrescentada.

A verificação é independente do Chaves e do keystore privado. Confere assinatura
com `apksigner`, identidade e ausência de debuggable com `aapt`, HTML/CSS/JS/mídia
no APK, checksum, fonte integrada, configuração de versão e fingerprint daquele
commit. Confere também as versões fixadas na fonte e o conteúdo das notas.
Arquivos extras ou links simbólicos no pacote de entrega são recusados.

Isso verifica consistência e proveniência registrada, não promete build
reproduzível byte a byte ou atestação remota. A publicação, obtenção do anexo e
a instalação real continuam em I5; a preservação no upgrade, em I6. Consulte a
[central](plans/01-primeira-versao-instalavel.md#5-central-de-progresso) para o
estado e as evidências finais de cada etapa.
