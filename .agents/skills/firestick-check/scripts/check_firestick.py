#!/usr/bin/env python3
"""Diagnóstico ADB delimitado. Saída privada; nunca persistir no Git."""

import argparse
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[4]


def run_adb(adb, args, timeout):
    try:
        result = subprocess.run(
            [adb, *args], capture_output=True, timeout=timeout, check=False
        )
        if result.returncode:
            return None  # Não divulgar stderr, que pode incluir endereços.
        return result.stdout
    except subprocess.TimeoutExpired:
        return None


def parse_address(value):
    # IPv4 explícito evita URLs, flags, descoberta e conexões a hosts ambíguos.
    host, separator, port = value.strip().partition(":")
    ipaddress.IPv4Address(host)
    number = int(port) if separator else 5555
    if not 1 <= number <= 65535:
        raise ValueError("Porta inválida")
    return host, number


def diagnose(address, timeout, screenshot=False):
    adb = shutil.which("adb")
    if not adb:
        return {"status": "adb_missing"}, 2
    host, port = parse_address(address)
    target = f"{host}:{port}"
    report = {"status": "unreachable", "tcp": False}
    try:
        with socket.create_connection((host, port), timeout=min(timeout, 3)):
            report["tcp"] = True
    except OSError:
        return report, 1

    # Uma tentativa; não reinicia nem desconecta outros dispositivos.
    run_adb(adb, ["connect", target], timeout)
    devices = run_adb(adb, ["devices"], timeout)
    state = "not_listed"
    if devices is not None:
        for line in devices.decode(errors="replace").splitlines():
            parts = line.split()
            if len(parts) >= 2 and parts[0] == target:
                state = parts[1]
                break
    report.update(status=state, adb_state=state)
    if state != "device":
        return report, 1

    def shell(*args):
        data = run_adb(adb, ["-s", target, "shell", *args], timeout)
        return data.decode(errors="replace").strip() if data is not None else None

    identity = {
        "manufacturer": shell("getprop", "ro.product.manufacturer"),
        "model": shell("getprop", "ro.product.model"),
        "device": shell("getprop", "ro.product.device"),
    }
    report["identity"] = identity
    if any(not value for value in identity.values()):
        report["status"] = "identity_unavailable"
        return report, 1
    if (identity["manufacturer"].lower(), identity["model"], identity["device"]) != (
        "amazon", "AFTKRT", "karat"
    ):
        report["status"] = "identity_mismatch"
        return report, 1

    missing = []

    def collect(name, command, pattern=None):
        value = shell(*command)
        if pattern and value:
            value = "\n".join(
                line.strip() for line in value.splitlines() if re.search(pattern, line)
            )
        if not value:
            missing.append(name)
        report[name] = value or None

    collect("android", ["getprop", "ro.build.version.release"])
    collect("webview", ["dumpsys", "webviewupdate"], r"Current WebView package")
    collect("display_size", ["wm", "size"], r"Physical size|Override size")
    collect("screen", ["dumpsys", "power"], r"mWakefulness=|Display Power: state=")
    collect("foreground", ["dumpsys", "activity", "activities"],
            r"mResumedActivity|topResumedActivity")
    collect("memory", ["cat", "/proc/meminfo"], r"^MemTotal:|^MemAvailable:")
    collect("storage", ["df", "-k", "/data"])

    if screenshot:
        data = run_adb(adb, ["-s", target, "exec-out", "screencap", "-p"], timeout)
        if data and data.startswith(b"\x89PNG\r\n\x1a\n"):
            # O diretório precisa estar fora do repo, mesmo com TMPDIR customizado.
            directory = Path(tempfile.mkdtemp(prefix="memora-firestick-"))
            if directory.resolve().is_relative_to(ROOT):
                directory.rmdir()
                missing.append("screenshot_outside_repository")
            else:
                path = directory / "screen.png"
                path.write_bytes(data)
                path.chmod(0o600)
                report["screenshot"] = str(path)
        else:
            missing.append("screenshot")

    report["unavailable"] = missing
    report["status"] = "partial" if missing else "ok"
    return report, 3 if missing else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--address", help="IPv4[:porta]; não persistido")
    parser.add_argument("--timeout", type=float, default=8, help="Segundos por comando (1–30)")
    parser.add_argument("--screenshot", action="store_true")
    args = parser.parse_args()
    try:
        if not 1 <= args.timeout <= 30:
            raise ValueError("Timeout deve estar entre 1 e 30 segundos")
        address = args.address or os.environ.get("FIRESTICK_ADB_ADDRESS")
        if not address:
            config = ROOT / ".local/firestick.json"
            if config.exists():
                address = json.loads(config.read_text())["address"]
        if not isinstance(address, str) or not address.strip():
            raise ValueError("Defina FIRESTICK_ADB_ADDRESS ou .local/firestick.json")
        parse_address(address)
        report, code = diagnose(address, args.timeout, args.screenshot)
    except (ValueError, KeyError, TypeError, OSError):
        report, code = {"status": "configuration_error",
                        "hint": "Verifique endereço IPv4[:porta], JSON local e timeout de 1–30s."}, 2
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
