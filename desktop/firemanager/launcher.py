from __future__ import annotations

from pathlib import Path

from .adb import AdbClient
from .errors import AdbError

PACKAGE = "com.cleantablet.launcher"
ACTIVITY = f"{PACKAGE}/.HomeActivity"


def install_launcher(client: AdbClient, serial: str, apk: Path) -> str:
    output = client.install(serial, apk)
    if "Success" not in output:
        raise AdbError(output.strip() or "APK installation was not confirmed")
    started = client.shell(serial, "am", "start", "-W", "-n", ACTIVITY)
    if "Status: ok" not in started and "Complete" not in started:
        raise AdbError(f"Launcher installed but did not start: {started.strip()}")
    return output.strip()
