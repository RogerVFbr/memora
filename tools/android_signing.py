"""Custódia local: PKCS#12 fora de Git e senha no Chaves, nunca na linha de comando."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / '.local/signing.json'
PIN = ROOT / 'android-release-signing.sha256'
SERVICE = 'br.com.memora.release-signing'
ACCOUNT = 'memora-release'


class SigningError(Exception):
    pass


def command(args, *, env=None, data=None):
    result = subprocess.run(args, input=data, capture_output=True, env=env, timeout=40)
    if result.returncode:
        # Tool diagnostics can contain private paths or credential prompts.
        raise SigningError('Falha em ferramenta de assinatura; credenciais não foram impressas.')
    return result.stdout


def keytool():
    java_home = os.environ.get('JAVA_HOME')
    if not java_home or not (Path(java_home) / 'bin/keytool').is_file():
        raise SigningError('Configure JAVA_HOME com o JDK 17 antes de executar.')
    return str(Path(java_home) / 'bin/keytool')


def read_config(path=DEFAULT_CONFIG):
    try:
        config = json.loads(Path(path).read_text())
        required = {'keystore', 'backup', 'alias', 'keychainService', 'keychainAccount'}
        if set(config) != required or not all(isinstance(config[k], str) and config[k] for k in required):
            raise ValueError()
        for name in ('keystore', 'backup'):
            original = Path(config[name]).expanduser()
            item = original.resolve()
            if item.is_relative_to(ROOT) or not item.is_file() or original.is_symlink():
                raise ValueError()
            if item.stat().st_mode & 0o077:
                raise ValueError()
        if hashlib.sha256(Path(config['keystore']).read_bytes()).digest() != hashlib.sha256(Path(config['backup']).read_bytes()).digest():
            raise ValueError()
        return config
    except (OSError, ValueError, KeyError, TypeError):
        raise SigningError('Configuração de assinatura ausente/inconsistente ou backup inválido; prepare a custódia local.') from None


def password(config):
    if sys.platform != 'darwin':
        raise SigningError('Esta custódia usa o Chaves do macOS; não há senha em arquivos de configuração.')
    value = command(['/usr/bin/security', 'find-generic-password', '-a', config['keychainAccount'], '-s', config['keychainService'], '-w']).decode().rstrip('\n')
    if len(value) < 16:
        raise SigningError('Credencial de assinatura indisponível/inválida.')
    return value


def certificate(config, secret):
    env = os.environ.copy()
    env['MEMORA_SIGNING_PASSWORD'] = secret
    der = command([keytool(), '-exportcert', '-keystore', config['keystore'], '-storetype', 'PKCS12', '-alias', config['alias'], '-storepass:env', 'MEMORA_SIGNING_PASSWORD'], env=env)
    return hashlib.sha256(der).hexdigest()


def checked_credentials(path=DEFAULT_CONFIG):
    config = read_config(path)
    secret = password(config)
    digest = certificate(config, secret)
    try:
        expected = PIN.read_text().strip()
    except OSError:
        raise SigningError('Fingerprint público da assinatura não está configurado.') from None
    if digest != expected:
        raise SigningError('Certificado da chave difere do fingerprint público versionado.')
    return config, secret, digest


def provision(backup_directory):
    if sys.platform != 'darwin':
        raise SigningError('A preparação desta custódia requer macOS.')
    keytool()
    if DEFAULT_CONFIG.exists():
        _, _, digest = checked_credentials()
        print('Custódia existente e backup verificados. Certificado SHA-256:', digest)
        return
    primary = Path.home() / '.local/share/memora/signing/release.p12'
    backup = Path(backup_directory).expanduser().resolve() / 'release.p12'
    if primary.is_relative_to(ROOT) or backup.is_relative_to(ROOT):
        raise SigningError('Chave e backup precisam ficar fora do repositório.')
    primary.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    backup.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if primary.parent.stat().st_dev == backup.parent.stat().st_dev:
        raise SigningError('Use outro volume para o backup cifrado da chave.')
    if PIN.exists() and not primary.exists():
        raise SigningError('Há identidade pública reservada: recupere a chave existente, sem gerar outra.')
    os.chmod(primary.parent, 0o700)
    os.chmod(backup.parent, 0o700)
    config = {'keystore': str(primary), 'backup': str(backup), 'alias': ACCOUNT,
              'keychainService': SERVICE, 'keychainAccount': ACCOUNT}
    lookup = subprocess.run(['/usr/bin/security', 'find-generic-password', '-a', ACCOUNT, '-s', SERVICE, '-w'], capture_output=True, timeout=15)
    if lookup.returncode == 0:
        secret = lookup.stdout.decode().rstrip('\n')
    else:
        if primary.exists() or PIN.exists():
            raise SigningError('Restaure a senha existente no Chaves antes de recuperar a custódia.')
        secret = secrets.token_urlsafe(32)
        # -w last prompts on stdin; secret is never an argv entry or printed.
        command(['/usr/bin/security', 'add-generic-password', '-a', ACCOUNT, '-s', SERVICE,
                 '-T', '/usr/bin/security', '-w'], data=(secret + '\n' + secret + '\n').encode())
        if password(config) != secret:
            raise SigningError('A credencial não foi preservada no Chaves; nenhuma chave foi gerada.')
    env = os.environ.copy()
    env['MEMORA_SIGNING_PASSWORD'] = secret
    if not primary.exists():
        if backup.exists():
            raise SigningError('Já há backup: recupere a chave, sem substituir a identidade.')
        command([keytool(), '-genkeypair', '-keystore', str(primary), '-storetype', 'PKCS12',
                 '-alias', ACCOUNT, '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                 '-dname', 'CN=Memora Local Release,O=Memora',
                 '-storepass:env', 'MEMORA_SIGNING_PASSWORD', '-keypass:env', 'MEMORA_SIGNING_PASSWORD'], env=env)
        os.chmod(primary, 0o600)
    digest = certificate(config, secret)
    if PIN.exists() and PIN.read_text().strip() != digest:
        raise SigningError('A chave existente não corresponde à identidade pública; nada foi substituído.')
    if backup.exists():
        if backup.read_bytes() != primary.read_bytes():
            raise SigningError('Backup já existente é diferente; nada foi substituído.')
    else:
        with backup.open('xb') as output:
            output.write(primary.read_bytes())
        os.chmod(backup, 0o600)
    DEFAULT_CONFIG.parent.mkdir(exist_ok=True, mode=0o700)
    with DEFAULT_CONFIG.open('x') as output:
        json.dump(config, output, indent=2)
        output.write('\n')
    os.chmod(DEFAULT_CONFIG, 0o600)
    if not PIN.exists():
        with PIN.open('x') as output:
            output.write(digest + '\n')
    checked_credentials()
    print('Chave permanente e backup cifrado preparados. Certificado SHA-256:', digest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='operation', required=True)
    create = sub.add_parser('prepare')
    create.add_argument('--backup-dir', required=True)
    sub.add_parser('check')
    args = parser.parse_args()
    try:
        if args.operation == 'prepare':
            provision(args.backup_dir)
        else:
            _, _, digest = checked_credentials()
            print('Custódia/backup/certificado verificados. SHA-256:', digest)
    except (SigningError, OSError, subprocess.SubprocessError) as error:
        print(str(error) if isinstance(error, SigningError) else 'Falha local na custódia de assinatura.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
