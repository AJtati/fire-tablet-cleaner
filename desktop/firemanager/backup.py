from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import tempfile

from .models import DeviceInfo, PackageState, Profile

FORMAT_VERSION = 1


def default_backup_root() -> Path:
    if os.name == "nt":
        base = Path(os.environ.get("LOCALAPPDATA", Path.home()))
    elif sys_platform() == "darwin":
        base = Path.home() / "Library" / "Application Support"
    else:
        base = Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local" / "state"))
    return base / "fire-tablet-cleaner" / "backups"


def sys_platform() -> str:
    import sys
    return sys.platform


def device_key(serial: str) -> str:
    return hashlib.sha256(serial.encode("utf-8")).hexdigest()[:16]


def write_json_atomic(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp_name, path)
    finally:
        try:
            os.unlink(temp_name)
        except FileNotFoundError:
            pass


def create_backup(root: Path, info: DeviceInfo, state: PackageState, profile: Profile, selected: list[str]) -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = root / device_key(info.serial) / f"{stamp}-{profile.profile_id}.json"
    value = {
        "format": FORMAT_VERSION,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "device": {
            "model": info.model,
            "sdk": info.sdk,
            "android": info.android,
            "fire_os": info.fire_os,
            "fingerprint": info.fingerprint,
        },
        "profile_id": profile.profile_id,
        "selected": selected,
        "states": {package: ("disabled" if package in state.disabled else "enabled") for package in selected},
        "completed": [],
    }
    write_json_atomic(path, value)
    return path


def read_backup(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("format") != FORMAT_VERSION or not isinstance(value.get("states"), dict):
        raise ValueError("Unsupported or damaged backup file")
    return value


def mark_completed(path: Path, package: str) -> None:
    value = read_backup(path)
    if package not in value["completed"]:
        value["completed"].append(package)
    write_json_atomic(path, value)
