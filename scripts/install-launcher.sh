#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then
  echo "usage: scripts/install-launcher.sh <adb serial>" >&2
  exit 2
fi
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
APK="$ROOT/launcher/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "Build the launcher first: scripts/build-launcher.sh" >&2
  exit 1
fi
adb -s "$1" install -r "$APK"
adb -s "$1" shell am start -W -n com.cleantablet.launcher/.HomeActivity
echo "Launcher installed and opened. Amazon Home was left enabled."
