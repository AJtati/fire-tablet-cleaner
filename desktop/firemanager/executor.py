from __future__ import annotations

from pathlib import Path

from .adb import AdbClient
from .backup import mark_completed, read_backup
from .errors import AdbError
from .probe import package_state


def disable_packages(client: AdbClient, serial: str, packages: list[str], backup_path: Path) -> list[str]:
    completed: list[str] = []
    for package in packages:
        client.shell(serial, "pm", "disable-user", "--user", "0", package)
        state = package_state(client, serial)
        if package not in state.disabled:
            raise AdbError(f"Fire OS did not disable {package}; stopped before the next package.")
        mark_completed(backup_path, package)
        completed.append(package)
    return completed


def restore_backup(client: AdbClient, serial: str, backup_path: Path, expected_fingerprint: str) -> list[str]:
    backup = read_backup(backup_path)
    if backup["device"].get("fingerprint") != expected_fingerprint:
        raise AdbError("This backup belongs to a different firmware fingerprint.")
    restored: list[str] = []
    for package, original in backup["states"].items():
        command = "disable-user" if original == "disabled" else "enable"
        args = ["pm", command]
        args += ["--user", "0"]
        args.append(package)
        client.shell(serial, *args)
        state = package_state(client, serial)
        disabled = package in state.disabled
        if disabled != (original == "disabled"):
            raise AdbError(f"Could not restore the original state of {package}.")
        restored.append(package)
    return restored
