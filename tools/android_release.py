"""Empacotamento local verificável; não publica releases nem instala aplicativos."""
import argparse
from datetime import datetime, timezone, timedelta
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

from .android_signing import DEFAULT_CONFIG, SigningError, checked_credentials

ROOT = Path(__file__).resolve().parents[1]
VERSION_FILE = 'android-release.properties'
PIN_FILE = 'android-release-signing.sha256'
SEMVER = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?')
HEX = re.compile(r'[0-9a-f]{64}')
TOOL_KEYS = {'java', 'gradle', 'agp', 'kotlin', 'node', 'npm', 'androidBuildTools', 'compileSdk'}


class ReleaseError(Exception):
    pass


def run(args, *, env=None, secret=None):
    result = subprocess.run([str(x) for x in args], cwd=ROOT, env=env, capture_output=True, text=True, timeout=600)
    output = result.stdout + '\n' + result.stderr
    if result.returncode:
        if secret:
            output = output.replace(secret, '[redigido]')
        private = ROOT / '.local/release-tool-error.log'
        private.parent.mkdir(exist_ok=True)
        private.write_text(output)
        private.chmod(0o600)
        raise ReleaseError('Uma ferramenta local falhou; diagnóstico sanitizado em .local/release-tool-error.log.')
    return result.stdout


def git(*args):
    return run(['git', *args]).strip()


def parse_version(text):
    result = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            raise ReleaseError('Configuração de versão inválida.')
        key, value = line.split('=', 1)
        if key in result:
            raise ReleaseError('Configuração de versão contém campo duplicado.')
        result[key] = value.strip()
    if set(result) != {'applicationId', 'versionName', 'versionCode'}:
        raise ReleaseError('Campos da configuração de versão são inválidos.')
    match = SEMVER.fullmatch(result['versionName'])
    if not match or (match[4] and any(x.isdigit() and len(x) > 1 and x.startswith('0') for x in match[4].split('.'))):
        raise ReleaseError('versionName precisa seguir SemVer 2.0.0.')
    if not re.fullmatch(r'[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+', result['applicationId']):
        raise ReleaseError('applicationId inválido.')
    if not re.fullmatch(r'[1-9][0-9]*', result['versionCode']) or int(result['versionCode']) > 2_147_483_647:
        raise ReleaseError('versionCode precisa ser inteiro Android positivo.')
    result['versionCode'] = int(result['versionCode'])
    return result


def version():
    return parse_version((ROOT / VERSION_FILE).read_text())


def sdk_tools():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    source = (ROOT / 'apps/android/app/build.gradle.kts').read_text()
    revision = re.search(r'buildToolsVersion = "([^"]+)"', source)[1]
    if not sdk:
        raise ReleaseError('Configure ANDROID_HOME para o SDK local.')
    directory = Path(sdk) / 'build-tools' / revision
    if not all((directory / name).is_file() for name in ['aapt', 'apksigner']):
        raise ReleaseError('Build Tools fixadas não estão disponíveis no SDK local.')
    return directory


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def inspect_apk(apk):
    tools = sdk_tools()
    signed = run([tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk])
    certificates = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', signed, re.M)
    if len(certificates) != 1:
        raise ReleaseError('APK precisa ter exatamente um certificado de assinatura.')
    badging = run([tools / 'aapt', 'dump', 'badging', apk])
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    if not package or 'application-debuggable' in badging:
        raise ReleaseError('APK de release inválido ou marcado como debuggable.')
    try:
        with zipfile.ZipFile(apk) as archive:
            names = archive.namelist()
            if 'assets/index.html' not in names or len([n for n in names if n.startswith('assets/media/') and n.endswith('.jpg')]) != 4:
                raise ReleaseError('HTML ou mídia demonstrativa não estão embarcados corretamente.')
            scripts = b''.join(archive.read(n) for n in names if n.startswith('assets/') and n.endswith('.js'))
            if package[3].encode() not in scripts or not any(n.startswith('assets/') and n.endswith('.css') for n in names):
                raise ReleaseError('Versão ou frontend estático não encontrado nos assets.')
    except zipfile.BadZipFile:
        raise ReleaseError('Arquivo APK inválido.') from None
    return {'applicationId': package[1], 'versionCode': int(package[2]), 'versionName': package[3],
            'certificateSha256': certificates[0].lower()}


def pinned_tools(root_build, wrapper, app_build):
    return {'gradle': re.search(r'gradle-([0-9.]+)-bin', wrapper)[1],
            'agp': re.search(r'id\("com.android.application"\) version "([^"]+)"', root_build)[1],
            'kotlin': re.search(r'id\("org.jetbrains.kotlin.android"\) version "([^"]+)"', root_build)[1],
            'androidBuildTools': re.search(r'buildToolsVersion = "([^"]+)"', app_build)[1],
            'compileSdk': re.search(r'compileSdk = ([0-9]+)', app_build)[1]}


def tools_metadata():
    java_home = os.environ.get('JAVA_HOME')
    if not java_home:
        raise ReleaseError('Configure JAVA_HOME para o JDK 17.')
    result = subprocess.run([str(Path(java_home) / 'bin/java'), '-version'], capture_output=True, text=True, timeout=10)
    match = re.search(r'version "([^"]+)"', result.stdout + result.stderr)
    if not match or not match[1].startswith('17.'):
        raise ReleaseError('O build de entrega requer JDK 17.')
    root_build = (ROOT / 'apps/android/build.gradle.kts').read_text()
    wrapper = (ROOT / 'apps/android/gradle/wrapper/gradle-wrapper.properties').read_text()
    app_build = (ROOT / 'apps/android/app/build.gradle.kts').read_text()
    return {'java': match[1], 'node': run(['node', '--version']).strip(),
            'npm': run(['npm', '--version']).strip(), **pinned_tools(root_build, wrapper, app_build)}


def notes(version_name, commit):
    content = git('show', f'{commit}:docs/releases/{version_name}.md')
    return content.rstrip() + f'\n\nOrigem: `{commit}`.\n'


def validate_metadata(info):
    keys = {'schemaVersion', 'applicationId', 'versionName', 'versionCode', 'commit', 'builtAtUtc',
            'tools', 'certificateSha256', 'apkSha256'}
    if not isinstance(info, dict) or set(info) != keys or type(info['schemaVersion']) is not int or info['schemaVersion'] != 1:
        raise ReleaseError('Metadados de entrega inválidos.')
    if type(info['versionCode']) is not int or not isinstance(info['tools'], dict) or set(info['tools']) != TOOL_KEYS:
        raise ReleaseError('Tipos/campos dos metadados são inválidos.')
    if not all(isinstance(v, str) and re.fullmatch(r'[0-9A-Za-z._+ -]+', v) for v in info['tools'].values()):
        raise ReleaseError('Versões de ferramentas não podem conter caminhos privados.')
    if not isinstance(info['commit'], str) or not re.fullmatch(r'[0-9a-f]{40}', info['commit']):
        raise ReleaseError('Commit de origem inválido.')
    if not all(isinstance(info[k], str) and HEX.fullmatch(info[k]) for k in ['certificateSha256', 'apkSha256']):
        raise ReleaseError('Digest inválido nos metadados.')
    try:
        when = datetime.fromisoformat(info['builtAtUtc'].replace('Z', '+00:00'))
        if when.utcoffset() != timedelta(0):
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        raise ReleaseError('Horário de build precisa ser UTC explícito.') from None


def verify(directory):
    directory = Path(directory).resolve()
    info = json.loads((directory / 'build-info.json').read_text())
    validate_metadata(info)
    name = f"memora-{info['versionName']}.apk"
    if not SEMVER.fullmatch(info['versionName']):
        raise ReleaseError('SemVer dos metadados é inválida.')
    expected_files = {name, 'SHA256SUMS', 'build-info.json', 'release-notes.md'}
    if {p.name for p in directory.iterdir()} != expected_files or any(p.is_symlink() or not p.is_file() for p in directory.iterdir()):
        raise ReleaseError('Diretório de entrega precisa conter apenas os quatro arquivos esperados.')
    source_version = parse_version(git('show', f"{info['commit']}:{VERSION_FILE}"))
    if any(info[key] != source_version[key] for key in source_version):
        raise ReleaseError('Versão/identidade dos metadados difere da fonte identificada.')
    check = subprocess.run(['git', 'merge-base', '--is-ancestor', info['commit'], 'origin/main'], cwd=ROOT, capture_output=True)
    if check.returncode:
        raise ReleaseError('Origem da entrega não está integrada em origin/main.')
    source_cert = git('show', f"{info['commit']}:{PIN_FILE}")
    source_pins = pinned_tools(git('show', f"{info['commit']}:apps/android/build.gradle.kts"),
                               git('show', f"{info['commit']}:apps/android/gradle/wrapper/gradle-wrapper.properties"),
                               git('show', f"{info['commit']}:apps/android/app/build.gradle.kts"))
    if any(info['tools'][key] != value for key, value in source_pins.items()) or not info['tools']['java'].startswith('17.'):
        raise ReleaseError('Versões de ferramentas diferem da configuração da fonte.')
    actual = inspect_apk(directory / name)
    if any(info[key] != actual[key] for key in actual) or info['certificateSha256'] != source_cert:
        raise ReleaseError('APK, certificado, metadados e fonte não são consistentes.')
    actual_hash = digest(directory / name)
    if info['apkSha256'] != actual_hash or (directory / 'SHA256SUMS').read_text() != f'{actual_hash}  {name}\n':
        raise ReleaseError('Checksum não corresponde aos bytes finais do APK.')
    if (directory / 'release-notes.md').read_text() != notes(info['versionName'], info['commit']):
        raise ReleaseError('Notas de entrega diferem da origem identificada.')
    return info


def check_progression(current, certificate):
    completed = ROOT / 'builds'
    if completed.exists():
        for path in completed.glob('*/build-info.json'):
            if path.parent.name.startswith('.'):
                continue
            previous = json.loads(path.read_text())
            validate_metadata(previous)
            if previous['applicationId'] != current['applicationId'] or previous['certificateSha256'] != certificate:
                raise ReleaseError('Identidade/certificado difere de uma entrega anterior.')
            if current['versionCode'] <= previous['versionCode']:
                raise ReleaseError('versionCode precisa superar as entregas locais anteriores.')
    for tag in git('tag', '--list', 'v*').splitlines():
        source = subprocess.run(['git', 'show', f'{tag}:{VERSION_FILE}'], cwd=ROOT, capture_output=True, text=True)
        if source.returncode:
            continue
        previous = parse_version(source.stdout)
        if git('show', f'{tag}:{PIN_FILE}') != certificate:
            raise ReleaseError('Certificado difere de uma tag de entrega anterior.')
        if current['applicationId'] != previous['applicationId'] or current['versionCode'] <= previous['versionCode']:
            raise ReleaseError('Identidade/contador Android conflita com uma tag anterior.')


def build(signing_config=DEFAULT_CONFIG):
    # Missing configuration fails before Gradle and before writing delivery files.
    from .android_signing import read_config
    read_config(signing_config)
    current = version()
    target = ROOT / 'builds' / current['versionName']
    if target.exists():
        raise ReleaseError('Entrega já existe; não sobrescreva. Reserve outra versão.')
    if git('status', '--porcelain'):
        raise ReleaseError('Checkout precisa estar limpo para registrar a origem do build.')
    commit = git('rev-parse', 'HEAD')
    check = subprocess.run(['git', 'merge-base', '--is-ancestor', commit, 'origin/main'], cwd=ROOT, capture_output=True)
    if check.returncode:
        raise ReleaseError('Integre a fonte em main antes de gerar a entrega final.')
    if git('tag', '--list', 'v' + current['versionName']):
        raise ReleaseError('Tag da versão já existe; não substitua uma entrega publicada.')
    config, secret, cert = checked_credentials(signing_config)
    check_progression(current, cert)
    tools = tools_metadata()
    env = os.environ.copy()
    env.update(MEMORA_KEYSTORE_FILE=config['keystore'], MEMORA_KEY_ALIAS=config['alias'], MEMORA_SIGNING_PASSWORD=secret)
    run(['npm', '--prefix', 'apps', 'ci'])
    run(['npm', '--prefix', 'apps', 'run', 'test:presentation'])
    run([ROOT / 'apps/android/gradlew', '-p', ROOT / 'apps/android', '--no-daemon', 'clean',
         'assembleRelease', 'testReleaseUnitTest', 'lintRelease'], env=env, secret=secret)
    if git('status', '--porcelain') or git('rev-parse', 'HEAD') != commit:
        raise ReleaseError('A origem mudou durante o build; entrega não finalizada.')
    apk = ROOT / 'apps/android/app/build/outputs/apk/release/app-release.apk'
    actual = inspect_apk(apk)
    if any(actual[k] != current[k] for k in current) or actual['certificateSha256'] != cert:
        raise ReleaseError('APK assinado não corresponde à configuração de entrega.')
    info = {'schemaVersion': 1, **current, 'commit': commit,
            'builtAtUtc': datetime.now(timezone.utc).isoformat(timespec='seconds'),
            'tools': tools, 'certificateSha256': cert, 'apkSha256': digest(apk)}
    destination = ROOT / 'builds'
    destination.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.building-', dir=destination) as temporary:
        stage = Path(temporary)
        name = f"memora-{current['versionName']}.apk"
        shutil.copyfile(apk, stage / name)
        (stage / 'SHA256SUMS').write_text(f"{info['apkSha256']}  {name}\n")
        (stage / 'build-info.json').write_text(json.dumps(info, indent=2, ensure_ascii=False) + '\n')
        (stage / 'release-notes.md').write_text(notes(current['versionName'], commit))
        verify(stage)
        # copytree creates the target exclusively and refuses an existing path.
        shutil.copytree(stage, target)
    verify(target)
    return target


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='operation', required=True)
    create = commands.add_parser('build')
    create.add_argument('--signing-config', type=Path, default=DEFAULT_CONFIG)
    validate = commands.add_parser('verify')
    validate.add_argument('directory', type=Path)
    args = parser.parse_args()
    try:
        if args.operation == 'build':
            target = build(args.signing_config)
            print('Entrega assinada/verificada:', target.relative_to(ROOT))
        else:
            info = verify(args.directory)
            print('Entrega verificada:', info['versionName'], 'código', info['versionCode'], 'SHA-256', info['apkSha256'])
    except (ReleaseError, SigningError, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        error = sys.exc_info()[1]
        print(str(error) if isinstance(error, (ReleaseError, SigningError)) else 'Entrega inválida ou ferramenta local indisponível.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
