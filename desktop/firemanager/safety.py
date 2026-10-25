from __future__ import annotations

import re

from .errors import ProfileError
from .models import PackageState, PlanItem, Profile

PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
CORE_RE = re.compile(
    r"^(android|com\.android\.(systemui|shell|phone|settings)|"
    r"com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|"
    r"com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*)$"
)
CORE_OK = re.compile(r"^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$")
FIRE_NEVER_DISABLE = {
    "com.amazon.firelauncher",
    "com.amazon.device.settings",
    "com.amazon.device.software.ota",
    "com.amazon.device.software.ota.override",
}
FIRE_NEVER_PREFIXES = ("com.amazon.inputmethod.",)


def is_protected(package: str) -> bool:
    return bool(
        (CORE_RE.match(package) and not CORE_OK.match(package))
        or package in FIRE_NEVER_DISABLE
        or package.startswith(FIRE_NEVER_PREFIXES)
    )


def validate_profile(profile: Profile) -> None:
    for rule in profile.packages:
        if not PACKAGE_RE.fullmatch(rule.package):
            raise ProfileError(f"Invalid package name in {profile.source}: {rule.package!r}")
        if is_protected(rule.package):
            raise ProfileError(f"Protected Android package in {profile.source}: {rule.package}")
        if rule.risk == "essential" and rule.default:
            raise ProfileError(f"Essential package cannot be selected by default: {rule.package}")


def build_plan(profile: Profile, state: PackageState) -> list[PlanItem]:
    validate_profile(profile)
    return [PlanItem(
        rule=rule,
        installed=rule.package in state.installed,
        disabled=rule.package in state.disabled,
        selected=rule.default and rule.risk == "safe" and rule.package in state.installed and rule.package not in state.disabled,
    ) for rule in profile.packages]


def validate_selection(profile: Profile, packages: list[str]) -> list[str]:
    validate_profile(profile)
    rules = {rule.package: rule for rule in profile.packages}
    clean: list[str] = []
    for package in packages:
        rule = rules.get(package)
        if not rule:
            raise ProfileError(f"Package is not in the matched profile: {package}")
        if rule.risk == "essential" or is_protected(package):
            raise ProfileError(f"Package is protected from disabling: {package}")
        if package not in clean:
            clean.append(package)
    return clean
