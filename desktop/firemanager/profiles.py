from __future__ import annotations

import json
from pathlib import Path

from .errors import ProfileError
from .models import Compatibility, DeviceInfo, PackageRule, Profile

RISKS = {"safe", "optional", "advanced", "essential"}


def load_profile(path: Path) -> Profile:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise ProfileError(f"Cannot read profile {path}: {exc}") from exc
    required = ("id", "name", "models", "fingerprints", "sdk", "tested_on", "packages")
    missing = [key for key in required if key not in raw]
    if missing:
        raise ProfileError(f"{path}: missing {', '.join(missing)}")
    if not raw["fingerprints"]:
        raise ProfileError(f"{path}: a verified profile needs at least one exact fingerprint")
    seen: set[str] = set()
    rules: list[PackageRule] = []
    for item in raw["packages"]:
        risk = item.get("risk")
        package = item.get("package", "")
        if risk not in RISKS:
            raise ProfileError(f"{path}: invalid risk for {package!r}")
        if package in seen:
            raise ProfileError(f"{path}: duplicate package {package}")
        seen.add(package)
        rules.append(PackageRule(
            package=package,
            risk=risk,
            title=item.get("title", package),
            reason=item.get("reason", ""),
            effects=item.get("effects", ""),
            default=bool(item.get("default", False)),
        ))
    return Profile(
        profile_id=str(raw["id"]),
        name=str(raw["name"]),
        models=tuple(str(value) for value in raw["models"]),
        fingerprints=tuple(str(value) for value in raw["fingerprints"]),
        sdk=int(raw["sdk"]),
        tested_on=str(raw["tested_on"]),
        packages=tuple(rules),
        source=path,
        allow_home_assignment=bool(raw.get("allow_home_assignment", False)),
    )


def load_profiles(folder: Path) -> list[Profile]:
    if not folder.is_dir():
        return []
    return [load_profile(path) for path in sorted(folder.glob("*.json"))]


def compatibility(info: DeviceInfo, profiles: list[Profile]) -> Compatibility:
    model_candidates = [profile for profile in profiles if info.model in profile.models]
    exact = [profile for profile in model_candidates if info.fingerprint in profile.fingerprints and info.sdk == profile.sdk]
    if len(exact) == 1:
        return Compatibility(exact[0], True, f"Verified profile: {exact[0].name}")
    if len(exact) > 1:
        return Compatibility(None, False, "Multiple profiles match; changes are blocked until the profile database is fixed.")
    if model_candidates:
        return Compatibility(None, False, "The model is known, but this exact firmware fingerprint is not verified. Inventory only.")
    return Compatibility(None, False, "No verified profile matches this model and firmware. Inventory only.")
