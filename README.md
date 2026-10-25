# Fire Tablet Cleaner

A safety-first, no-root desktop manager and lightweight touchscreen launcher for Amazon Fire tablets.

The desktop application detects USB-connected tablets, identifies their exact firmware, inventories
packages, applies only exact-match verified debloat profiles, writes recovery records, restores prior
package states and installs the included launcher. The launcher provides a responsive app grid,
search, favourites, wallpaper access and portrait/landscape layouts without Internet permission,
advertising, analytics or background services.

## Safety status

This repository is implementation-complete but ships with **no claimed verified tablet profiles**.
That is intentional: a package list cannot become safe through code review alone. Until a profile for
an exact model and firmware fingerprint has passed the physical-device procedure, the manager stays
in inventory-only mode. Launcher installation remains available and never disables Amazon Home.

Supported application runtime:

| Fire OS | Android base | Launcher | Debloat engine |
|---|---:|---|---|
| 5 | Android 5.1 / API 22 | Supported by `minSdk 22`; physical testing required | Exact verified profile required |
| 6 | Android 7.1 / API 25 | Supported; physical testing required | Exact verified profile required |
| 7 | Android 9 / API 28 | Supported; first recommended test target | Exact verified profile required |
| 8 | Android 10/11 / API 29–30 | Supported; first recommended test target | Exact verified profile required |

No Fire OS version is promised to accept third-party default-Home assignment. Amazon Home remains
enabled, and this project does not use root, uninstall system packages or bypass protected packages.

## Requirements

- Python 3.9 or newer, including Tkinter
- Android SDK Platform Tools (`adb`)
- For the launcher build: JDK 17, Android SDK Platform 34 and Build Tools 34
- A data-capable USB cable

## Run the desktop manager

```sh
python3 -m venv .venv
.venv/bin/pip install -e .
.venv/bin/fire-tablet-manager
```

Windows activation and launch:

```powershell
py -m venv .venv
.venv\Scripts\pip install -e .
.venv\Scripts\fire-tablet-manager.exe
```

On the tablet, open **Settings → Device Options → About Fire Tablet**, tap the serial-number row
seven times, enable Developer Options and USB debugging, connect USB, and accept the computer's key.
The application reports unauthorized devices instead of attempting changes.

Useful CLI checks:

```sh
fire-tablet-manager-cli devices
fire-tablet-manager-cli probe <adb-serial>
fire-tablet-manager-cli scan <adb-serial>
```

## Build and install the launcher

Set `ANDROID_HOME` if the SDK is not in its standard location, then:

```sh
scripts/build-launcher.sh
scripts/install-launcher.sh <adb-serial>
```

On Windows, run `launcher\gradlew.bat lintRelease assembleRelease`, then choose the resulting APK
from the desktop manager's **Install launcher…** button.

The APK is written to `launcher/app/build/outputs/apk/release/app-release.apk`. The desktop GUI can
install the same file. Installation opens the launcher as an ordinary app/Home candidate; it does not
change the system's default Home or disable the Amazon launcher.

Touch and hold an app to add or remove it from favourites. Search matches labels and package names.
Use the Wallpaper button to open the wallpaper chooser when the firmware supplies one.

## Profiles and recovery

Read [profile authoring](docs/PROFILES.md), [physical testing](docs/TESTING.md) and
[recovery](docs/RECOVERY.md) before adding a verified device. Applying changes requires an exact
model, SDK and fingerprint match. The CLI also requires `--yes`; without it, it prints the plan only.

```sh
fire-tablet-manager-cli apply <serial> PACKAGE [PACKAGE ...]
fire-tablet-manager-cli apply <serial> PACKAGE [PACKAGE ...] --yes
fire-tablet-manager-cli restore <serial> /path/to/recovery.json
```

## Tests

```sh
python3 -m unittest discover -s tests -v
cd launcher && ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

The Python tests use fake ADB clients and never modify a connected device.

## Non-goals for 0.1

- Amazon Home disabling or accessibility-based Home interception
- root, bootloader changes or `pm uninstall --user 0`
- generic package recommendations for unknown firmware
- widgets, folders, cloud services, telemetry or automatic OTA blocking

## Acknowledgements

The protected-package and reversible-recovery approach is adapted from
[CleanYourTV](https://github.com/Gathin23/cleanyourtv), used under its MIT licence. Fire Tablet Cleaner
is a separate project because tablet UI and Fire OS compatibility require a different product and
physical-device test matrix.
