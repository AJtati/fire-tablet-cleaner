from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal

Risk = Literal["safe", "optional", "advanced", "essential"]


@dataclass(frozen=True)
class AdbDevice:
    serial: str
    state: str
    model_hint: str = ""
    transport_id: str = ""


@dataclass(frozen=True)
class DeviceInfo:
    serial: str
    model: str
    manufacturer: str
    sdk: int
    android: str
    fire_os: str
    fingerprint: str
    product: str = ""

    @property
    def display_name(self) -> str:
        return f"{self.model} — Fire OS {self.fire_os or 'unknown'} / Android {self.android or 'unknown'}"


@dataclass(frozen=True)
class PackageRule:
    package: str
    risk: Risk
    title: str
    reason: str
    effects: str = ""
    default: bool = False


@dataclass(frozen=True)
class Profile:
    profile_id: str
    name: str
    models: tuple[str, ...]
    fingerprints: tuple[str, ...]
    sdk: int
    tested_on: str
    packages: tuple[PackageRule, ...]
    source: Path
    allow_home_assignment: bool = False


@dataclass
class PackageState:
    installed: set[str] = field(default_factory=set)
    disabled: set[str] = field(default_factory=set)


@dataclass(frozen=True)
class PlanItem:
    rule: PackageRule
    installed: bool
    disabled: bool
    selected: bool


@dataclass(frozen=True)
class Compatibility:
    profile: Profile | None
    operations_allowed: bool
    reason: str
