from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from .adb import AdbClient
from .backup import create_backup, default_backup_root
from .errors import FireManagerError
from .executor import disable_packages, restore_backup
from .launcher import install_launcher
from .probe import package_state, probe_device
from .profiles import compatibility, load_profiles
from .safety import build_plan, validate_selection

ROOT = Path(__file__).resolve().parents[2]


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="fire-tablet-manager-cli", description="Safe Fire tablet manager")
    p.add_argument("--adb", help="path to adb")
    p.add_argument("--profiles", type=Path, default=ROOT / "profiles" / "verified")
    sub = p.add_subparsers(dest="command", required=True)
    sub.add_parser("devices", help="list connected ADB devices")
    for name in ("probe", "scan"):
        command = sub.add_parser(name)
        command.add_argument("serial")
    apply = sub.add_parser("apply")
    apply.add_argument("serial")
    apply.add_argument("packages", nargs="+")
    apply.add_argument("--backup-root", type=Path, default=default_backup_root())
    apply.add_argument("--yes", action="store_true", help="confirm the displayed operations")
    restore = sub.add_parser("restore")
    restore.add_argument("serial")
    restore.add_argument("backup", type=Path)
    install = sub.add_parser("install-launcher")
    install.add_argument("serial")
    install.add_argument("apk", type=Path)
    return p


def _device(client: AdbClient, serial: str):
    devices = {device.serial: device for device in client.devices()}
    if serial not in devices:
        raise FireManagerError(f"Device {serial!r} is not connected")
    if devices[serial].state != "device":
        raise FireManagerError(f"Device {serial!r} is {devices[serial].state}; accept the USB debugging prompt")
    return probe_device(client, serial)


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        client = AdbClient(args.adb)
        if args.command == "devices":
            for device in client.devices():
                print(f"{device.serial}\t{device.state}\t{device.model_hint}")
            return 0
        info = _device(client, args.serial)
        if args.command == "probe":
            print(json.dumps({key: value for key, value in vars(info).items() if key != "serial"}, indent=2))
            return 0
        if args.command == "install-launcher":
            print(install_launcher(client, args.serial, args.apk))
            return 0
        if args.command == "restore":
            restored = restore_backup(client, args.serial, args.backup, info.fingerprint)
            print(f"Restored {len(restored)} package states from {args.backup}")
            return 0

        profiles = load_profiles(args.profiles)
        match = compatibility(info, profiles)
        state = package_state(client, args.serial)
        if args.command == "scan":
            result = {
                "device": info.display_name,
                "fingerprint": info.fingerprint,
                "compatibility": match.reason,
                "operations_allowed": match.operations_allowed,
                "installed_packages": len(state.installed),
                "disabled_packages": sorted(state.disabled),
            }
            if match.profile:
                result["profile"] = match.profile.profile_id
                result["recommendations"] = [
                    {"package": item.rule.package, "risk": item.rule.risk, "selected": item.selected,
                     "disabled": item.disabled, "title": item.rule.title, "effects": item.rule.effects}
                    for item in build_plan(match.profile, state) if item.installed
                ]
            print(json.dumps(result, indent=2))
            return 0

        if not match.operations_allowed or not match.profile:
            raise FireManagerError(match.reason)
        selected = validate_selection(match.profile, args.packages)
        missing = [package for package in selected if package not in state.installed]
        if missing:
            raise FireManagerError("Not installed: " + ", ".join(missing))
        if not args.yes:
            print("Will disable for user 0:")
            for package in selected:
                print(f"  {package}")
            print("Re-run with --yes after reviewing this list.")
            return 2
        backup = create_backup(args.backup_root, info, state, match.profile, selected)
        completed = disable_packages(client, args.serial, selected, backup)
        print(f"Disabled {len(completed)} packages. Recovery file: {backup}")
        return 0
    except (FireManagerError, ValueError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
