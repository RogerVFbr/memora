import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import android_release as release


def config(name='0.1.0-alpha.1', code='1', package='br.com.memora'):
    return f'applicationId={package}\nversionName={name}\nversionCode={code}\n'


def metadata(code=1, cert='a' * 64):
    return {'schemaVersion': 1, 'applicationId': 'br.com.memora', 'versionName': '0.1.0-alpha.1',
            'versionCode': code, 'commit': 'b' * 40, 'builtAtUtc': '2026-10-25T00:00:00+00:00',
            'certificateSha256': cert, 'apkSha256': 'c' * 64,
            'tools': {key: '1.0' for key in release.TOOL_KEYS}}


class ReleaseContractTest(unittest.TestCase):
    def test_semver_prerelease_and_metadata_are_supported(self):
        value = release.parse_version(config('0.1.0-alpha.2+build.01', '2'))
        self.assertEqual(value['versionName'], '0.1.0-alpha.2+build.01')
        self.assertEqual(value['versionCode'], 2)

    def test_invalid_semver_cannot_be_used_as_an_artifact_path(self):
        for name in ['01.0.0', '0.1', '0.1.0-alpha.01', '../0.1.0', '0.1.0/other', '0.1.0-']:
            with self.subTest(name=name), self.assertRaises(release.ReleaseError):
                release.parse_version(config(name))

    def test_android_counter_and_package_are_validated(self):
        for code in ['0', '-1', '01', 'true', '2147483648']:
            with self.subTest(code=code), self.assertRaises(release.ReleaseError):
                release.parse_version(config(code=code))
        with self.assertRaises(release.ReleaseError):
            release.parse_version(config(package='../../private'))

    def test_duplicate_or_extra_properties_are_rejected(self):
        for extra in ['versionCode=2\n', 'password=do-not-store-secrets\n']:
            with self.assertRaises(release.ReleaseError):
                release.parse_version(config() + extra)

    def test_build_metadata_rejects_paths_and_missing_timezone(self):
        item = metadata()
        item['tools']['java'] = '/private/example/jdk'
        with self.assertRaises(release.ReleaseError):
            release.validate_metadata(item)
        item = metadata()
        item['builtAtUtc'] = '2026-10-25T00:00:00'
        with self.assertRaises(release.ReleaseError):
            release.validate_metadata(item)

    def test_metadata_integer_fields_cannot_be_booleans(self):
        for key in ['schemaVersion', 'versionCode']:
            item = metadata()
            item[key] = True
            with self.assertRaises(release.ReleaseError):
                release.validate_metadata(item)

    def test_android_counter_must_increase_even_when_semver_looks_newer(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            previous = root / 'builds/0.1.0-alpha.1'
            previous.mkdir(parents=True)
            (previous / 'build-info.json').write_text(json.dumps(metadata(code=5)))
            with patch.object(release, 'ROOT', root), patch.object(release, 'git', return_value=''):
                with self.assertRaises(release.ReleaseError):
                    release.check_progression(release.parse_version(config('999.0.0', '4')), 'a' * 64)
                release.check_progression(release.parse_version(config('0.1.0-alpha.2', '6')), 'a' * 64)

    def test_new_delivery_cannot_silently_change_certificate(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            previous = root / 'builds/0.1.0-alpha.1'
            previous.mkdir(parents=True)
            (previous / 'build-info.json').write_text(json.dumps(metadata()))
            with patch.object(release, 'ROOT', root), patch.object(release, 'git', return_value=''):
                with self.assertRaises(release.ReleaseError):
                    release.check_progression(release.parse_version(config('0.1.0-alpha.2', '2')), 'd' * 64)

    def test_existing_delivery_is_refused_before_running_build_tools(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'android-release.properties').write_text(config())
            (root / 'builds/0.1.0-alpha.1').mkdir(parents=True)
            with patch.object(release, 'ROOT', root), patch('tools.android_signing.read_config', return_value={}):
                with self.assertRaisesRegex(release.ReleaseError, 'Entrega já existe'):
                    release.build()


if __name__ == '__main__':
    unittest.main()
