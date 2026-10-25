from __future__ import annotations

import re

from .adb import AdbClient
from .errors import AdbError
from .models import DeviceInfo, PackageState

PROP_RE = re.compile(r"^\[([^]]+)]\s*:\s*\[(.*)]$")


def parse_props(text: str) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in text.splitlines():
        match = PROP_RE.match(line.strip())
        if match:
            props[match.group(1)] = match.group(2)
    return props


def _sdk(value: str) -> int:
    try:
        return int(value)
    except ValueError as exc:
        raise AdbError(f"The tablet returned an invalid Android SDK value: {value!r}") from exc


def probe_device(client: AdbClient, serial: str) -> DeviceInfo:
    props = parse_props(client.shell(serial, "getprop"))
    sdk = _sdk(props.get("ro.build.version.sdk", ""))
    fire_os = next((props[key] for key in (
        "ro.build.version.fireos", "ro.build.version.name", "ro.build.version.incremental"
    ) if props.get(key)), "")
    return DeviceInfo(
        serial=serial,
        model=props.get("ro.product.model", "Unknown model"),
        manufacturer=props.get("ro.product.manufacturer", ""),
        sdk=sdk,
        android=props.get("ro.build.version.release", ""),
        fire_os=fire_os,
        fingerprint=props.get("ro.build.fingerprint", ""),
        product=props.get("ro.product.device", ""),
    )


def _packages(text: str) -> set[str]:
    return {line.split(":", 1)[1].strip() for line in text.splitlines() if line.startswith("package:")}


def package_state(client: AdbClient, serial: str) -> PackageState:
    installed = _packages(client.shell(serial, "pm", "list", "packages", "--user", "0"))
    try:
        disabled = _packages(client.shell(serial, "pm", "list", "packages", "--user", "0", "-d"))
    except AdbError:
        disabled = _packages(client.shell(serial, "pm", "list", "packages", "-d"))
    if not installed:
        raise AdbError("The tablet returned no packages for user 0.")
    return PackageState(installed, disabled)
