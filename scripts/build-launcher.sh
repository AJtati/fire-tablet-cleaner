#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT/launcher"
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
printf '%s\n' "$ROOT/launcher/app/build/outputs/apk/release/app-release.apk"
