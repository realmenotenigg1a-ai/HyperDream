#!/usr/bin/env bash
set -euo pipefail
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
TOOLS="$SDK_ROOT/build-tools/35.0.0"
SOURCE="app/build/outputs/apk/debug/app-debug.apk"
DEST="build/distribution"
mkdir -p "$DEST"
"$TOOLS/aapt" dump badging "$SOURCE" > "$DEST/PACKAGE.txt"
VERSION=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" "$DEST/PACKAGE.txt" | head -n 1)
[[ "$VERSION" =~ ^[a-zA-Z0-9._-]+$ ]] || { echo 'Invalid APK version'; exit 1; }
REVISION="${GITHUB_SHA:-local}"
REVISION="${REVISION:0:8}"
NAME="HyperDream-${VERSION}-${REVISION}-debug.apk"
cp "$SOURCE" "$DEST/$NAME"
"$TOOLS/apksigner" verify --verbose --print-certs "$DEST/$NAME" > "$DEST/SIGNATURE.txt"
(cd "$DEST" && sha256sum "$NAME" > SHA256SUMS.txt)
printf 'Version: %s\nBuild: %s\nCommit: %s\nSigning: Android debug key (testing only)\nHyperOS 4 on-device verification: not performed\n' \
  "$VERSION" "$REVISION" "${GITHUB_SHA:-local}" > "$DEST/BUILD-INFO.txt"
