from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
from typing import Iterable

from .errors import AdbError
from .models import AdbDevice


def find_adb(explicit: str | None = None) -> str:
    candidates = [explicit, os.environ.get("ADB")]
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        candidates.append(str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")))
    candidates.append(shutil.which("adb"))
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return str(Path(candidate).resolve())
    raise AdbError("ADB was not found. Install Android SDK Platform Tools or set the ADB environment variable.")


class AdbClient:
    def __init__(self, adb_path: str | None = None, timeout: float = 30.0):
        self.adb_path = find_adb(adb_path)
        self.timeout = timeout

    def run(self, args: Iterable[str], *, serial: str | None = None, timeout: float | None = None) -> str:
        command = [self.adb_path]
        if serial:
            command += ["-s", serial]
        command += list(args)
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=timeout or self.timeout,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise AdbError(f"ADB failed: {exc}") from exc
        if result.returncode:
            detail = (result.stderr or result.stdout).strip() or f"exit status {result.returncode}"
            raise AdbError(detail)
        return result.stdout.replace("\r\n", "\n")

    def devices(self) -> list[AdbDevice]:
        output = self.run(["devices", "-l"])
        devices: list[AdbDevice] = []
        for line in output.splitlines()[1:]:
            fields = line.split()
            if len(fields) < 2:
                continue
            metadata = dict(field.split(":", 1) for field in fields[2:] if ":" in field)
            devices.append(AdbDevice(fields[0], fields[1], metadata.get("model", ""), metadata.get("transport_id", "")))
        return devices

    def shell(self, serial: str, *args: str, timeout: float | None = None) -> str:
        # Every argument is passed separately. No host shell or Android shell interpolation is used.
        return self.run(["shell", *args], serial=serial, timeout=timeout)

    def install(self, serial: str, apk: Path) -> str:
        if not apk.is_file():
            raise AdbError(f"Launcher APK not found: {apk}")
        return self.run(["install", "-r", str(apk)], serial=serial, timeout=180)
